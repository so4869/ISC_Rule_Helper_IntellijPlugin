package im.flare.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project

class IscCloudRuleRunConfigurationType : ConfigurationTypeBase(
    ID,
    "ISC Create XML and Validate Cloud Execution Rule",
    "Export SailPoint ISC Cloud Execution Rule XML",
    AllIcons.RunConfigurations.Application
) {
    companion object {
        const val ID = "IscCloudRuleRunConfiguration"
    }

    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId() = ID
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                IscCloudRuleRunConfiguration(project, this, "ISC Cloud Rule")
        })
    }
}
