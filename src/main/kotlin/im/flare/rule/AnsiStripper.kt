package im.flare.rule

private val ANSI_ESCAPE = Regex("\\[[0-9;]*[A-Za-z]")

fun String.stripAnsi(): String = ANSI_ESCAPE.replace(this, "")

// Standard 4-bit foreground colors
private val ANSI_FG = mapOf(
    30 to "#1e1e1e", 31 to "#cc0000", 32 to "#4e9a06", 33 to "#c4a000",
    34 to "#3465a4", 35 to "#75507b", 36 to "#06989a", 37 to "#d3d7cf",
    90 to "#888888", 91 to "#ef2929", 92 to "#8ae234", 93 to "#fce94f",
    94 to "#729fcf", 95 to "#ad7fa8", 96 to "#34e2e2", 97 to "#eeeeee"
)

private data class Fmt(
    val bold: Boolean = false,
    val underline: Boolean = false,
    val color: String? = null
) {
    val isPlain get() = !bold && !underline && color == null
    fun style() = buildList {
        color?.let { add("color:$it") }
        if (bold) add("font-weight:bold")
        if (underline) add("text-decoration:underline")
    }.joinToString(";")
}

/**
 * Converts ANSI escape sequences to an HTML <pre> block suitable for Markdown files.
 * Bold, underline, and foreground colors are mapped to inline <span style="..."> tags.
 * Text content is HTML-escaped.
 */
fun String.ansiToMarkdown(): String {
    val out = StringBuilder("<pre>\n")
    var fmt = Fmt()
    var spanOpen = false
    var i = 0

    fun closeSpan() {
        if (spanOpen) { out.append("</span>"); spanOpen = false }
    }

    fun applyFmt(next: Fmt) {
        if (next == fmt) return
        closeSpan()
        fmt = next
        if (!fmt.isPlain) {
            out.append("<span style=\"${fmt.style()}\">")
            spanOpen = true
        }
    }

    while (i < length) {
        val c = this[i]
        if (c == '' && i + 1 < length && this[i + 1] == '[') {
            var j = i + 2
            while (j < length && (this[j].isDigit() || this[j] == ';')) j++
            if (j >= length) { out.append(c); i++; continue }
            val cmd = this[j]; val param = substring(i + 2, j); i = j + 1
            if (cmd != 'm') continue

            val codes = if (param.isEmpty()) listOf(0)
                        else param.split(";").mapNotNull { it.toIntOrNull() }
            var next = fmt
            var k = 0
            while (k < codes.size) {
                next = when (val code = codes[k]) {
                    0            -> Fmt()
                    1            -> next.copy(bold = true)
                    4            -> next.copy(underline = true)
                    22           -> next.copy(bold = false)
                    24           -> next.copy(underline = false)
                    in 30..37,
                    in 90..97   -> next.copy(color = ANSI_FG[code])
                    38           -> when {
                        k + 2 < codes.size && codes[k + 1] == 5 -> { k += 2; next } // 256-color: skip
                        k + 4 < codes.size && codes[k + 1] == 2 -> {
                            val rgb = "rgb(${codes[k+2]},${codes[k+3]},${codes[k+4]})"
                            k += 4; next.copy(color = rgb)
                        }
                        else -> next
                    }
                    39           -> next.copy(color = null)
                    else         -> next
                }
                k++
            }
            applyFmt(next)
        } else {
            when (c) {
                '<'  -> out.append("&lt;")
                '>'  -> out.append("&gt;")
                '&'  -> out.append("&amp;")
                else -> out.append(c)
            }
            i++
        }
    }

    closeSpan()
    out.append("\n</pre>")
    return out.toString()
}
