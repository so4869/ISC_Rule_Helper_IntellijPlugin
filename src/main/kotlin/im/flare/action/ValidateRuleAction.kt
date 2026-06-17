package im.flare.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import im.flare.rule.IscSourceExtractor
import im.flare.rule.IscTemplateProcessor
import im.flare.rule.stripAnsi
import im.flare.runconfig.IscCloudRuleRunConfiguration
import java.io.File

private fun applyJavaEnv(pb: ProcessBuilder, javaHome: String) {
    pb.environment()["JAVA_HOME"] = javaHome
}

class ValidateRuleAction(
    private val config: IscCloudRuleRunConfiguration
) : AnAction(config.name) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = ValidateRuleGroup.getCurrentPsiClass(e) ?: return

        object : Task.Backgroundable(project, "Validating rule (${config.name})...", false) {
            private var output: String = ""
            private var fileName: String = ""

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val info = ReadAction.compute<im.flare.rule.IscRuleInfo?, Throwable> {
                    IscSourceExtractor.extract(psiClass)
                } ?: throw IllegalStateException("Failed to extract class info from ${psiClass.qualifiedName}")

                fileName = info.fileName?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(
                        "Cannot compute file name for ${psiClass.qualifiedName} — tenant/type/nm may be missing"
                    )

                indicator.text = "Generating XML..."
                val xml = IscTemplateProcessor.process(IscTemplateProcessor.DEFAULT_TEMPLATE, info)

                val execFile = File(config.validatorExecutable).absoluteFile
                val execDir = execFile.parentFile
                // Named after the rule so validator output shows a meaningful path.
                val tmpFile = File(execDir, "$fileName.xml")
                try {
                    tmpFile.writeText(xml, Charsets.UTF_8)
                    indicator.text = "Running validator..."
                    val pb = ProcessBuilder(execFile.absolutePath, "--file", tmpFile.absolutePath)
                        .directory(execDir)
                        .redirectErrorStream(true)
                    config.resolveJavaHome(project)?.let { applyJavaEnv(pb, it) }
                    output = pb.start().also { it.waitFor() }.inputStream.bufferedReader().readText().stripAnsi()
                } finally {
                    tmpFile.delete()
                }
            }

            override fun onSuccess() {
                ValidateRuleOutputDialog(project, fileName, output).show()
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(
                    project,
                    error.message ?: "Unknown error",
                    "Validate Rule Failed"
                )
            }
        }.queue()
    }
}
