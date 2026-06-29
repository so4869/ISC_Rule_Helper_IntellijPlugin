package im.flare.rule

import java.io.File

object IscTemplateProcessor {

    private val SOURCE_PATTERN = Regex("<Source>[\\s\\S]*?</Source>")
    private val RULE_TAG_PATTERN = Regex("(<Rule)(\\b[^>]*)(>)")

    val DEFAULT_TEMPLATE = """
        <?xml version='1.0' encoding='UTF-8'?>
        <!DOCTYPE Rule PUBLIC "sailpoint.dtd" "sailpoint.dtd">
        <Rule language="beanshell" name="${'$'}TEMPLATE.NAME" type="${'$'}TEMPLATE.TYPE">
            <Source></Source>
        </Rule>
    """.trimIndent()

    fun readTemplateText(templateDirectory: String, type: String): String {
        val file = File(templateDirectory, "$type.template.xml")
        return if (file.exists()) file.readText(Charsets.UTF_8) else DEFAULT_TEMPLATE
    }

    fun process(
        templateText: String,
        info: IscRuleInfo,
        existingId: String? = null,
        createdMs: Long? = null,
        modifiedMs: Long? = null
    ): String {
        val source = IscSourceExtractor.assembleSource(info)
        return templateText
            .replace("\$TEMPLATE.NAME", info.name ?: "")
            .replace("\$TEMPLATE.TYPE", info.type ?: "")
            .let { addRuleAttributes(it, existingId, createdMs, modifiedMs) }
            .let { xml -> SOURCE_PATTERN.replace(xml) { "<Source><![CDATA[\n$source\n]]></Source>" } }
    }

    private fun addRuleAttributes(
        xml: String,
        existingId: String?,
        createdMs: Long?,
        modifiedMs: Long?
    ): String {
        if (existingId == null && createdMs == null && modifiedMs == null) return xml
        return RULE_TAG_PATTERN.replace(xml) { match ->
            val idAttr = if (existingId != null) """ id="$existingId"""" else ""
            val trailingAttrs = buildString {
                if (createdMs != null) append(""" created="$createdMs"""")
                if (modifiedMs != null) append(""" modified="$modifiedMs"""")
            }
            "${match.groupValues[1]}$idAttr${match.groupValues[2]}$trailingAttrs${match.groupValues[3]}"
        }
    }
}
