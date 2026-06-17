package im.flare.runconfig

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class IscCloudRuleSettingsEditor : SettingsEditor<IscCloudRuleRunConfiguration>() {

    private val basePackagesField = JBTextField()

    private val outputDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Output Directory",
            "Choose the directory where Rule XML files will be saved",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }

    private val createSubDirCheckbox = JBCheckBox("Create new directory for each execution")
    private val datetimeRadio = JBRadioButton("DateTimeFormatter expression")
    private val staticRadio = JBRadioButton("Static value")
    private val subDirExpressionField = JBTextField()

    private val validatorExeField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Validator Executable",
            "Choose the SailPoint Rule Validator executable",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
    }

    private val javaHomeField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Java Home",
            "Choose the JDK home directory to use when running the validator",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }

    private val subDirOptionsPanel: JPanel

    init {
        ButtonGroup().apply { add(datetimeRadio); add(staticRadio) }
        datetimeRadio.isSelected = true

        val subDirExprNote = ComponentPanelBuilder.createCommentComponent(
            "DateTimeFormatter: pattern applied to current time (e.g. yyyy-MM-dd_HH-mm-ss). " +
                "Static: used as-is as the sub-directory name.",
            true
        )

        subDirOptionsPanel = FormBuilder.createFormBuilder()
            .addComponent(datetimeRadio)
            .addComponent(staticRadio)
            .addLabeledComponent("Expression:", subDirExpressionField, true)
            .addComponent(subDirExprNote)
            .panel
            .apply {
                border = JBUI.Borders.emptyLeft(24)
                isVisible = false
            }

        createSubDirCheckbox.addActionListener {
            subDirOptionsPanel.isVisible = createSubDirCheckbox.isSelected
            subDirOptionsPanel.parent?.revalidate()
            subDirOptionsPanel.parent?.repaint()
        }
    }

    override fun createEditor(): JComponent {
        val basePackagesNote = ComponentPanelBuilder.createCommentComponent(
            "Comma-separated list of base packages to scan (e.g. com.example.rules). " +
                "Classes must have 'private static boolean PERFORM_ISC_SERVER = true'.",
            true
        )
        val outputDirNote = ComponentPanelBuilder.createCommentComponent(
            "Directory where Rule XML files and validator output will be written. " +
                "Defaults to {projectDir}/output if left empty.",
            true
        )
        val validatorNote = ComponentPanelBuilder.createCommentComponent(
            "Path to the SailPoint Rule Validator executable. " +
                "Invoked as: <executable> --file <xml-file>. Output saved as <FILE_NAME>.validator.out.",
            true
        )

        val sourcePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Base Packages:", basePackagesField, true)
            .addComponent(basePackagesNote)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Source Filter"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val outputPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Output Directory:", outputDirField, true)
            .addComponent(outputDirNote)
            .addComponent(createSubDirCheckbox)
            .addComponent(subDirOptionsPanel)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Output"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val javaHomeNote = ComponentPanelBuilder.createCommentComponent(
            "JDK home directory (JAVA_HOME) used when running the validator. " +
                "Leave empty to use the project SDK.",
            true
        )

        val validatorPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Executable:", validatorExeField, true)
            .addComponent(validatorNote)
            .addLabeledComponent("Java Home:", javaHomeField, true)
            .addComponent(javaHomeNote)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Rule Validator"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        return FormBuilder.createFormBuilder()
            .addComponent(sourcePanel)
            .addVerticalGap(8)
            .addComponent(outputPanel)
            .addVerticalGap(8)
            .addComponent(validatorPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(8) }
    }

    override fun resetEditorFrom(config: IscCloudRuleRunConfiguration) {
        basePackagesField.text = config.basePackages
        outputDirField.text = config.outputDirectory
        validatorExeField.text = config.validatorExecutable
        javaHomeField.text = config.javaHome

        createSubDirCheckbox.isSelected = config.createSubDir
        when (config.subDirMode) {
            SubDirMode.DATETIME -> datetimeRadio.isSelected = true
            SubDirMode.STATIC   -> staticRadio.isSelected = true
        }
        subDirExpressionField.text = config.subDirExpression
        subDirOptionsPanel.isVisible = config.createSubDir
    }

    override fun applyEditorTo(config: IscCloudRuleRunConfiguration) {
        config.basePackages = basePackagesField.text.trim()
        config.outputDirectory = outputDirField.text.trim()
        config.validatorExecutable = validatorExeField.text.trim()
        config.javaHome = javaHomeField.text.trim()

        config.createSubDir = createSubDirCheckbox.isSelected
        config.subDirMode = if (datetimeRadio.isSelected) SubDirMode.DATETIME else SubDirMode.STATIC
        config.subDirExpression = subDirExpressionField.text.trim()
    }
}
