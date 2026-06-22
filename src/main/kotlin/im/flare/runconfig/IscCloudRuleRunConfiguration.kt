package im.flare.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jdom.Element

enum class SubDirMode { DATETIME, STATIC }
enum class ValidatorOutputFormat { TXT, MD }

class IscCloudRuleRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<RunProfileState>(project, factory, name) {

    var basePackages: String = ""
    var outputDirectory: String = ""          // blank → {projectBasePath}/output
    var validatorExecutable: String = ""
    var javaHome: String = ""                 // blank → project SDK home

    var createSubDir: Boolean = false
    var subDirMode: SubDirMode = SubDirMode.DATETIME
    var subDirExpression: String = ""

    var validatorOutputFormat: ValidatorOutputFormat = ValidatorOutputFormat.MD

    fun getBasePackageList(): List<String> =
        basePackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Returns the effective Java home: configured value, then project SDK, then null. */
    fun resolveJavaHome(project: Project): String? =
        javaHome.ifBlank { ProjectRootManager.getInstance(project).projectSdk?.homePath }
            ?.takeIf { it.isNotBlank() }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        basePackages = element.getAttributeValue(ATTR_BASE_PACKAGES) ?: ""
        outputDirectory = element.getAttributeValue(ATTR_OUTPUT_DIR) ?: ""
        validatorExecutable = element.getAttributeValue(ATTR_VALIDATOR_EXE) ?: ""
        javaHome = element.getAttributeValue(ATTR_JAVA_HOME) ?: ""
        createSubDir = element.getAttributeValue(ATTR_CREATE_SUB_DIR)?.toBoolean() ?: false
        subDirMode = element.getAttributeValue(ATTR_SUB_DIR_MODE)
            ?.let { runCatching { SubDirMode.valueOf(it) }.getOrNull() }
            ?: SubDirMode.DATETIME
        subDirExpression = element.getAttributeValue(ATTR_SUB_DIR_EXPR) ?: ""
        validatorOutputFormat = element.getAttributeValue(ATTR_VALIDATOR_OUTPUT_FORMAT)
            ?.let { runCatching { ValidatorOutputFormat.valueOf(it) }.getOrNull() }
            ?: ValidatorOutputFormat.MD
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(ATTR_BASE_PACKAGES, basePackages)
        element.setAttribute(ATTR_OUTPUT_DIR, outputDirectory)
        element.setAttribute(ATTR_VALIDATOR_EXE, validatorExecutable)
        element.setAttribute(ATTR_JAVA_HOME, javaHome)
        element.setAttribute(ATTR_CREATE_SUB_DIR, createSubDir.toString())
        element.setAttribute(ATTR_SUB_DIR_MODE, subDirMode.name)
        element.setAttribute(ATTR_SUB_DIR_EXPR, subDirExpression)
        element.setAttribute(ATTR_VALIDATOR_OUTPUT_FORMAT, validatorOutputFormat.name)
    }

    override fun getConfigurationEditor() = IscCloudRuleSettingsEditor()

    override fun checkConfiguration() {
        if (basePackages.isBlank()) throw RuntimeConfigurationError("Base packages must not be empty")
        if (validatorExecutable.isBlank()) throw RuntimeConfigurationError("Validator executable must not be empty")
        if (createSubDir && subDirExpression.isBlank())
            throw RuntimeConfigurationError("Sub-directory expression must not be empty")
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        IscCloudRuleRunState(environment, this)

    companion object {
        private const val ATTR_BASE_PACKAGES = "basePackages"
        private const val ATTR_OUTPUT_DIR = "outputDirectory"
        private const val ATTR_VALIDATOR_EXE = "validatorExecutable"
        private const val ATTR_JAVA_HOME = "javaHome"
        private const val ATTR_CREATE_SUB_DIR = "createSubDir"
        private const val ATTR_SUB_DIR_MODE = "subDirMode"
        private const val ATTR_SUB_DIR_EXPR = "subDirExpression"
        private const val ATTR_VALIDATOR_OUTPUT_FORMAT = "validatorOutputFormat"
    }
}
