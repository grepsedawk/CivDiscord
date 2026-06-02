package io.github.grepsedawk.civdiscord.core.relay

sealed class SnitchPing {
    abstract val mention: String

    data class Role(val id: Long) : SnitchPing() {
        override val mention: String get() = "<@&$id>"
    }

    data class User(val id: Long) : SnitchPing() {
        override val mention: String get() = "<@$id>"
    }

    // Discord renders <@&{guildId}> as a literal "@@everyone" pill instead of a real ping.
    // The everyone ping is fired by the literal substring "@everyone" plus
    // allowed_mentions.parse including "everyone" — not via the role-mention path.
    data object Everyone : SnitchPing() {
        override val mention: String get() = "@everyone"
    }

    companion object {
        private val ROLE = Regex("^<@&(\\d+)>$")
        private val USER = Regex("^<@!?(\\d+)>$")

        fun parse(s: String?): SnitchPing? {
            if (s.isNullOrBlank()) return null
            val trimmed = s.trim()
            if (trimmed.equals("@everyone", ignoreCase = false)) return Everyone
            ROLE.matchEntire(trimmed)?.let {
                val id = it.groupValues[1].toLongOrNull() ?: return null
                return Role(id)
            }
            USER.matchEntire(trimmed)?.let {
                val id = it.groupValues[1].toLongOrNull() ?: return null
                return User(id)
            }
            return null
        }
    }
}
