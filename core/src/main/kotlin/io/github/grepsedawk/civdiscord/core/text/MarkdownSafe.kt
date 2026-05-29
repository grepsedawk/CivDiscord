package io.github.grepsedawk.civdiscord.core.text

object MarkdownSafe {
    fun code(s: String): String = s.replace("`", "")

    fun text(s: String): String = s
        .replace("\\", "\\\\")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("~", "\\~")
        .replace("`", "\\`")
        .replace("|", "\\|")
        .replace(">", "\\>")
}
