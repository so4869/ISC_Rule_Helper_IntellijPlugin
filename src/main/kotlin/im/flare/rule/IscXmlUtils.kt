package im.flare.rule

import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.io.StringReader

object IscXmlUtils {

    private const val XML_PREAMBLE =
        "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<!DOCTYPE Rule PUBLIC \"sailpoint.dtd\" \"sailpoint.dtd\">"

    // SAXBuilder that skips external DTD fetches; JDOM preserves CDATA via its built-in LexicalHandler.
    private fun saxBuilder() = SAXBuilder().apply {
        setEntityResolver(EntityResolver { _, _ -> InputSource(StringReader("")) })
    }

    private fun outputter() = XMLOutputter(Format.getPrettyFormat())

    /**
     * Strips id/created/modified attributes from a server Rule XML,
     * sets name to [backupName], and returns the result with XML preamble.
     */
    fun buildBackupXml(xml: String, backupName: String): String {
        val doc = saxBuilder().build(StringReader(xml))
        val root = doc.rootElement
        val rule = if (root.name == "Rule") root else root.getChild("Rule")
            ?: throw IllegalStateException("No <Rule> element found in server XML")
        rule.removeAttribute("id")
        rule.removeAttribute("created")
        rule.removeAttribute("modified")
        rule.setAttribute("name", backupName)
        return "$XML_PREAMBLE\n${outputter().outputString(rule)}"
    }

    /**
     * Extracts the <Rule> element XML string from a server response.
     * Handles a <sailpoint> wrapper if present.
     */
    fun extractRuleXml(xml: String): String? = runCatching {
        val doc = saxBuilder().build(StringReader(xml))
        val root = doc.rootElement
        val rule = if (root.name == "Rule") root else root.getChild("Rule")
        rule?.let { outputter().outputString(it) }
    }.getOrNull()

    fun extractRuleDates(xml: String): Pair<Long?, Long?> = runCatching {
        val doc = saxBuilder().build(StringReader(xml))
        val root = doc.rootElement
        val rule = if (root.name == "Rule") root else root.getChild("Rule") ?: return@runCatching null to null
        rule.getAttributeValue("created")?.toLongOrNull() to rule.getAttributeValue("modified")?.toLongOrNull()
    }.getOrElse { null to null }
}
