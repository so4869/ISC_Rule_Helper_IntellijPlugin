package im.flare.rule

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier

object IscSourceExtractor {

    fun extract(psiClass: PsiClass): IscRuleInfo? {
        val psiFile = psiClass.containingFile as? PsiJavaFile ?: return null
        val qualifiedName = psiClass.qualifiedName ?: return null

        val imports = psiFile.importList
            ?.allImportStatements
            ?.joinToString("\n") { it.text }
            ?: ""

        val type = extractAnyStringField(psiClass, "type")
        val tenant = extractAnyStringField(psiClass, "tenant")
        val nm = extractAnyStringField(psiClass, "nm")

        val executeMethod = psiClass.findMethodsByName("execute", false).firstOrNull()
            ?: return null

        val executeBody = extractBody(executeMethod)

        val helperMethods = collectHelperMethods(executeMethod, psiClass, mutableSetOf(executeMethod.name))
            .joinToString("\n\n") { method ->
                val dedented = dedent(method.text)
                "/* Start Method ${method.name} */\n$dedented\n/* End Method ${method.name} */"
            }

        return IscRuleInfo(
            qualifiedName = qualifiedName,
            imports = imports,
            helperMethods = helperMethods,
            executeBody = executeBody,
            type = type,
            tenant = tenant,
            nm = nm
        )
    }

    fun assembleSource(info: IscRuleInfo): String = buildString {
        if (info.imports.isNotBlank()) {
            append(info.imports)
            append("\n\n")
        }
        if (info.helperMethods.isNotBlank()) {
            append(dedent(info.helperMethods))
            append("\n\n")
        }
        append(dedent(info.executeBody))
    }.trim().let { stripGenerics(it) }

    private fun stripGenerics(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) -> {
                    val end = source.indexOf('\n', i).let { if (it == -1) source.length else it + 1 }
                    sb.append(source, i, end)
                    i = end
                }
                source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i + 2).let { if (it == -1) source.length else it + 2 }
                    sb.append(source, i, end)
                    i = end
                }
                source[i] == '"' -> {
                    val end = skipStringLiteral(source, i)
                    sb.append(source, i, end)
                    i = end
                }
                source[i] == '\'' -> {
                    val end = skipCharLiteral(source, i)
                    sb.append(source, i, end)
                    i = end
                }
                source[i] == '<' && i > 0 && isIdentChar(source[i - 1]) -> {
                    val end = findGenericEnd(source, i)
                    if (end >= 0) i = end + 1 else sb.append(source[i++])
                }
                else -> sb.append(source[i++])
            }
        }
        return sb.toString()
    }

    private fun isIdentChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun findGenericEnd(source: String, start: Int): Int {
        var depth = 1
        var i = start + 1
        while (i < source.length) {
            when (source[i]) {
                '<' -> depth++
                '>' -> { if (--depth == 0) return i }
                ';', '{', '}', '(', ')', '=', '+', '-', '*', '/', '%', '!', '"', '\'' -> return -1
            }
            i++
        }
        return -1
    }

    private fun skipStringLiteral(source: String, start: Int): Int {
        var i = start + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return source.length
    }

    private fun skipCharLiteral(source: String, start: Int): Int {
        var i = start + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                else -> i++
            }
        }
        return source.length
    }

    private fun dedent(code: String): String {
        val lines = code.lines()
        val minIndent = lines
            .filter { it.isNotBlank() }
            .minOfOrNull { line -> line.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: 0 }
            ?: 0
        if (minIndent == 0) return code
        return lines.joinToString("\n") { line ->
            if (line.isBlank()) "" else line.drop(minIndent)
        }
    }

    private fun extractStringField(psiClass: PsiClass, fieldName: String): String? =
        psiClass.fields
            .find { field ->
                field.name == fieldName &&
                field.hasModifierProperty(PsiModifier.PRIVATE) &&
                field.hasModifierProperty(PsiModifier.STATIC) &&
                field.type.canonicalText == "java.lang.String"
            }
            ?.let { (it.initializer as? PsiLiteralExpression)?.value as? String }

    // Extracts a String field by name regardless of access modifier or static-ness.
    private fun extractAnyStringField(psiClass: PsiClass, fieldName: String): String? =
        psiClass.fields
            .find { field ->
                field.name == fieldName &&
                field.type.canonicalText == "java.lang.String"
            }
            ?.let { (it.initializer as? PsiLiteralExpression)?.value as? String }

    private fun extractBody(method: PsiMethod): String {
        val body = method.body ?: return ""
        val text = body.text
        return text.substring(1, text.length - 1)
    }

    private fun collectHelperMethods(
        method: PsiMethod,
        ownerClass: PsiClass,
        visited: MutableSet<String>
    ): List<PsiMethod> {
        val result = mutableListOf<PsiMethod>()

        method.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                val resolved = expression.resolveMethod() ?: return
                if (resolved.containingClass?.qualifiedName == ownerClass.qualifiedName &&
                    visited.add(resolved.name)
                ) {
                    result.add(resolved)
                    result.addAll(collectHelperMethods(resolved, ownerClass, visited))
                }
            }
        })

        return result
    }
}
