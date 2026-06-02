package io.github.grepsedawk.civdiscord.velocity.discord

import java.util.UUID

object SkinUrl {
    private const val SIZE = 128

    fun avatar(uuid: UUID): String = "https://mc-heads.net/avatar/$uuid/$SIZE"

    fun avatar(uuid: String): String = "https://mc-heads.net/avatar/$uuid/$SIZE"
}
