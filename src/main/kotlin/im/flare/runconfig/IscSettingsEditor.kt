package im.flare.runconfig

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class IscSettingsEditor : SettingsEditor<IscRunConfiguration>() {

    private val tenantUrlField = JBTextField().apply {
        emptyText.text = "https://tenant.identitynow.com"
    }
    private val clientIdField = JBTextField()
    private val clientSecretField = JBPasswordField()

    override fun resetEditorFrom(config: IscRunConfiguration) {
        tenantUrlField.text = config.tenantUrl
        clientIdField.text = config.clientId
        clientSecretField.text = config.clientSecret
    }

    override fun applyEditorTo(config: IscRunConfiguration) {
        config.tenantUrl = tenantUrlField.text.trim()
        config.clientId = clientIdField.text.trim()
        config.clientSecret = String(clientSecretField.password)
    }

    override fun createEditor(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Tenant URL:", tenantUrlField, true)
            .addLabeledComponent("Client ID:", clientIdField, true)
            .addLabeledComponent("Client Secret:", clientSecretField, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel
}
