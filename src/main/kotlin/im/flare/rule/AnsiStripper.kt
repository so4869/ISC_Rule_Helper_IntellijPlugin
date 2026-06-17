package im.flare.rule

private val ANSI_ESCAPE = Regex("\\[[0-9;]*[A-Za-z]")

fun String.stripAnsi(): String = ANSI_ESCAPE.replace(this, "")
