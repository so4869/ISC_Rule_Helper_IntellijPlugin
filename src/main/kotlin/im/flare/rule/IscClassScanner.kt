package im.flare.rule

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope

object IscClassScanner {

    fun findEligibleClasses(project: Project, basePackages: List<String>): List<PsiClass> {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        return basePackages
            .flatMap { pkg ->
                val psiPackage = facade.findPackage(pkg) ?: return@flatMap emptyList()
                collectClasses(psiPackage, scope)
            }
            .distinctBy { it.qualifiedName }
            .filter { hasValidateFlag(it) }
    }

    private fun collectClasses(pkg: PsiPackage, scope: GlobalSearchScope): List<PsiClass> {
        val result = mutableListOf<PsiClass>()
        result.addAll(pkg.getClasses(scope))
        for (sub in pkg.getSubPackages(scope)) {
            result.addAll(collectClasses(sub, scope))
        }
        return result
    }

    // Eligible if any field named VALIDATE exists with boolean type and initializer true.
    // Access modifier and static-ness are not checked.
    fun hasValidateFlag(psiClass: PsiClass): Boolean =
        psiClass.fields.any { field ->
            field.name == "VALIDATE" &&
            field.type == PsiType.BOOLEAN &&
            field.initializer?.text?.trim() == "true"
        }
}
