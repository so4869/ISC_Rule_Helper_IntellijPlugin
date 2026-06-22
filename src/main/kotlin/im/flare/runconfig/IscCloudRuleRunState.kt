package im.flare.runconfig

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import im.flare.rule.IscClassScanner
import im.flare.rule.IscRuleInfo
import im.flare.rule.IscSourceExtractor
import im.flare.rule.IscTemplateProcessor
import im.flare.rule.ansiToMarkdown
import im.flare.rule.stripAnsi
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class IscCloudRuleRunState(
    private val environment: ExecutionEnvironment,
    private val config: IscCloudRuleRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val processHandler = createProcessHandler()

        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console
        console.attachToProcess(processHandler)
        processHandler.startNotify()

        object : Task.Backgroundable(project, "Scanning ISC Cloud Rule classes...", false) {
            private var ruleInfos: List<IscRuleInfo> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                console.info("Scanning packages: ${config.getBasePackageList().joinToString()}\n")
                ruleInfos = ReadAction.compute<List<IscRuleInfo>, Throwable> {
                    IscClassScanner.findEligibleClasses(project, config.getBasePackageList())
                        .mapNotNull { IscSourceExtractor.extract(it) }
                }
                console.info("Found ${ruleInfos.size} eligible class(es).\n\n")
            }

            override fun onSuccess() {
                exportRules(ruleInfos, processHandler, console)
            }

            override fun onThrowable(error: Throwable) {
                console.error("Scan failed: ${error.message}\n")
                processHandler.destroyProcess()
            }
        }.queue()

        return DefaultExecutionResult(console, processHandler)
    }

    private fun resolveOutputDir(): File {
        val basePath = config.outputDirectory.ifBlank {
            "${environment.project.basePath}/output"
        }
        val baseDir = File(basePath)

        if (!config.createSubDir) return baseDir

        val subName = when (config.subDirMode) {
            SubDirMode.DATETIME -> DateTimeFormatter.ofPattern(config.subDirExpression)
                .format(LocalDateTime.now())
            SubDirMode.STATIC -> config.subDirExpression
        }
        return File(baseDir, subName)
    }

    private fun exportRules(
        infos: List<IscRuleInfo>,
        processHandler: ProcessHandler,
        console: ConsoleView
    ) {
        object : Task.Backgroundable(environment.project, "Exporting ISC Cloud Rules...", false) {
            private var successCount = 0
            private var skipCount = 0

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                val outputDir = resolveOutputDir().also {
                    it.mkdirs()
                    console.info("Output directory: ${it.absolutePath}\n\n")
                }

                infos.forEachIndexed { index, info ->
                    indicator.fraction = (index + 1).toDouble() / infos.size
                    indicator.text = "Exporting ${info.qualifiedName}..."

                    val baseFileName = info.fileName
                    if (!isValidFileName(baseFileName)) {
                        console.warn(
                            "  [SKIP] ${info.qualifiedName} — computed file name is ${
                                if (baseFileName == null) "missing (tenant/type/nm may be null)"
                                else "invalid ('$baseFileName')"
                            }\n"
                        )
                        skipCount++
                        return@forEachIndexed
                    }

                    val xmlFileName = "$baseFileName.xml"
                    val xmlFile = File(outputDir, xmlFileName)
                    val xml = IscTemplateProcessor.process(IscTemplateProcessor.DEFAULT_TEMPLATE, info)
                    xmlFile.writeText(xml, Charsets.UTF_8)
                    console.info("  [OK] ${info.qualifiedName} → $xmlFileName\n")

                    val outExt = if (config.validatorOutputFormat == ValidatorOutputFormat.MD) "md" else "txt"
                    runValidator(
                        config.validatorExecutable,
                        xmlFile,
                        File(outputDir, "$xmlFileName.validator.$outExt"),
                        console
                    )
                    successCount++
                }
            }

            override fun onSuccess() {
                processHandler.destroyProcess()
                console.info(
                    "\nDone: $successCount exported" +
                        (if (skipCount > 0) ", $skipCount skipped" else "") + ".\n"
                )
            }

            override fun onThrowable(error: Throwable) {
                console.error("Export failed: ${error.message}\n")
                processHandler.destroyProcess()
            }
        }.queue()
    }

    private fun isValidFileName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.none { it in ILLEGAL_FILENAME_CHARS } && !name.contains('/') && !name.contains('\\')
    }

    private fun runValidator(executable: String, xmlFile: File, outFile: File, console: ConsoleView) {
        try {
            val execFile = File(executable).absoluteFile
            // ProcessBuilder passes each arg as a single token — no shell parsing, spaces are safe.
            val pb = ProcessBuilder(execFile.absolutePath, "--file", xmlFile.absolutePath)
                .directory(execFile.parentFile)
                .redirectErrorStream(true)
            config.resolveJavaHome(environment.project)?.let { applyJavaEnv(pb, it) }
            val raw = pb.start().also { it.waitFor() }.inputStream.bufferedReader().readText()
            val output = if (config.validatorOutputFormat == ValidatorOutputFormat.MD) raw.ansiToMarkdown() else raw.stripAnsi()
            outFile.writeText(output, Charsets.UTF_8)
            console.info("  [Validator] ${outFile.name} written\n")
        } catch (e: Exception) {
            console.error("  [Validator] ${xmlFile.name}: ${e.message}\n")
        }
    }

    private fun applyJavaEnv(pb: ProcessBuilder, javaHome: String) {
        pb.environment()["JAVA_HOME"] = javaHome
    }

    private fun createProcessHandler(): ProcessHandler = object : ProcessHandler() {
        override fun destroyProcessImpl() { notifyProcessTerminated(0) }
        override fun detachProcessImpl() { notifyProcessDetached() }
        override fun detachIsDefault(): Boolean = false
        override fun getProcessInput(): OutputStream? = null
    }

    private fun ConsoleView.info(text: String) = print(text, ConsoleViewContentType.NORMAL_OUTPUT)
    private fun ConsoleView.warn(text: String) = print(text, ConsoleViewContentType.LOG_WARNING_OUTPUT)
    private fun ConsoleView.error(text: String) = print(text, ConsoleViewContentType.ERROR_OUTPUT)

    companion object {
        private val ILLEGAL_FILENAME_CHARS = setOf(':', '*', '?', '"', '<', '>', '|')
    }
}
