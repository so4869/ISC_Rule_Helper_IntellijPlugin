package im.flare.runconfig

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class IscRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<RunProfileState>(project, factory, name) {

    var tenantUrl: String = ""
    var clientId: String = ""

    var clientSecret: String
        get() = PasswordSafe.instance.getPassword(credentialAttributes()) ?: ""
        set(value) { PasswordSafe.instance.setPassword(credentialAttributes(), value.ifEmpty { null }) }

    private fun credentialAttributes() = CredentialAttributes(
        generateServiceName("ISC_Rule_Helper", name),
        clientId.ifEmpty { "default" }
    )

    override fun readExternal(element: Element) {
        super.readExternal(element)
        tenantUrl = element.getAttributeValue(ATTR_TENANT_URL) ?: ""
        clientId = element.getAttributeValue(ATTR_CLIENT_ID) ?: ""
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(ATTR_TENANT_URL, tenantUrl)
        element.setAttribute(ATTR_CLIENT_ID, clientId)
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = IscSettingsEditor()

    override fun checkConfiguration() {
        if (tenantUrl.isBlank()) throw RuntimeConfigurationError("Tenant URL must not be empty")
        if (clientId.isBlank()) throw RuntimeConfigurationError("Client ID must not be empty")
        if (clientSecret.isBlank()) throw RuntimeConfigurationError("Client Secret must not be empty")
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        IscRunProfileState(this)

    companion object {
        private const val ATTR_TENANT_URL = "tenantUrl"
        private const val ATTR_CLIENT_ID = "clientId"
    }
}

private class IscRunProfileState(private val config: IscRunConfiguration) : RunProfileState {
    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val console = ConsoleViewImpl(config.project, true)
        val handler = IscProcessHandler(config, console)
        console.attachToProcess(handler)
        handler.startNotify()
        return DefaultExecutionResult(console, handler)
    }
}

private class IscProcessHandler(
    private val config: IscRunConfiguration,
    private val console: ConsoleView
) : ProcessHandler() {

    override fun startNotify() {
        super.startNotify()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                console.print("Connecting to ${config.tenantUrl}...\n", ConsoleViewContentType.NORMAL_OUTPUT)
                val token = fetchToken()
                console.print("Token obtained successfully.\n\n", ConsoleViewContentType.NORMAL_OUTPUT)
                console.print("Access Token:\n$token\n", ConsoleViewContentType.NORMAL_OUTPUT)
            } catch (e: Exception) {
                console.print("Error: ${e.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
            } finally {
                notifyProcessTerminated(0)
            }
        }
    }

    private fun fetchToken(): String {
        val uri = URI(config.tenantUrl.trimEnd('/'))
        val tenantName = uri.host.substringBefore('.')
        val tokenUrl = "https://$tenantName.api.identitynow.com/oauth/token"

        val body = "grant_type=client_credentials" +
            "&client_id=${URLEncoder.encode(config.clientId, "UTF-8")}" +
            "&client_secret=${URLEncoder.encode(config.clientSecret, "UTF-8")}"

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
        }

        return """"access_token"\s*:\s*"([^"]+)"""".toRegex()
            .find(response.body())
            ?.groupValues?.get(1)
            ?: throw RuntimeException("access_token not found in response")
    }

    override fun destroyProcessImpl() = notifyProcessTerminated(0)
    override fun detachProcessImpl() = notifyProcessDetached()
    override fun detachIsDefault() = false
    override fun getProcessInput(): OutputStream? = null
}
