package im.flare.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project

class IscRunConfigurationType : ConfigurationTypeBase(
    ID,
    "ISC Connection",
    "SailPoint IdentityNow tenant connection",
    AllIcons.RunConfigurations.Remote
) {
    companion object {
        const val ID = "IscRunConfiguration"
    }

    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId() = ID
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                IscRunConfiguration(project, this, "ISC Connection")
        })
    }
}
