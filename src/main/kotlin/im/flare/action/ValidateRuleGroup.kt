package im.flare.action

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import im.flare.rule.IscClassScanner
import im.flare.runconfig.IscCloudRuleRunConfiguration

class ValidateRuleGroup : ActionGroup() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        val psiClass = getCurrentPsiClass(e)
        if (psiClass == null || !isEligible(psiClass)) {
            e.presentation.isVisible = false
            return
        }
        val hasConfigs = RunManager.getInstance(project).allSettings
            .any { it.configuration is IscCloudRuleRunConfiguration &&
                   (it.configuration as IscCloudRuleRunConfiguration).validatorExecutable.isNotBlank() }
        e.presentation.isVisible = hasConfigs
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val event = e ?: return AnAction.EMPTY_ARRAY
        val project = event.project ?: return AnAction.EMPTY_ARRAY

        return RunManager.getInstance(project).allSettings
            .mapNotNull { it.configuration as? IscCloudRuleRunConfiguration }
            .filter { it.validatorExecutable.isNotBlank() }
            .map { ValidateRuleAction(it) }
            .toTypedArray()
    }

    companion object {

        fun getCurrentPsiClass(e: AnActionEvent): PsiClass? {
            val project = e.project ?: return null

            // 1. Editor — class at caret, fall back to first class in file
            val editor = e.getData(CommonDataKeys.EDITOR)
            val psiFile = e.getData(CommonDataKeys.PSI_FILE)
            if (editor != null && psiFile is PsiJavaFile) {
                val element = psiFile.findElementAt(editor.caretModel.offset)
                PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.let { return it }
                return psiFile.classes.firstOrNull()
            }

            // 2. PSI_ELEMENT (project / directory tree)
            val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
            if (psiElement is PsiClass) return psiElement
            if (psiElement is PsiJavaFile) return psiElement.classes.firstOrNull()
            PsiTreeUtil.getParentOfType(psiElement, PsiClass::class.java)?.let { return it }

            // 3. PSI_FILE fallback
            if (psiFile is PsiJavaFile) return psiFile.classes.firstOrNull()

            // 4. VIRTUAL_FILE → resolve via PsiManager
            val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            if (virtualFile != null) {
                val resolved = PsiManager.getInstance(project).findFile(virtualFile)
                if (resolved is PsiJavaFile) return resolved.classes.firstOrNull()
            }

            return null
        }

        fun isEligible(psiClass: PsiClass): Boolean =
            IscClassScanner.hasValidateFlag(psiClass)
    }
}
