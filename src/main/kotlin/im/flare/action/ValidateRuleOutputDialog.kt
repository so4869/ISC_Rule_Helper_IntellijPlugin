package im.flare.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTextArea

class ValidateRuleOutputDialog(
    project: Project,
    fileName: String,
    private val output: String
) : DialogWrapper(project, false) {

    init {
        title = "Validator Output — $fileName"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val textArea = JTextArea(output).apply {
            isEditable = false
            font = JBUI.Fonts.create("JetBrains Mono", 12)
                ?: font.deriveFont(12f)
            lineWrap = false
        }
        return JBScrollPane(textArea).apply {
            preferredSize = Dimension(800, 500)
        }
    }
}
