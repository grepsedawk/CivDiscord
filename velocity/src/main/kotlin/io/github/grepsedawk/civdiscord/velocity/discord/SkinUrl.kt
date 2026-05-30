package io.github.grepsedawk.civdiscord.velocity.discord

import java.util.UUID

object SkinUrl {
    private const val SIZE = 128

    fun head(uuid: UUID): String = "https://mc-heads.net/head/$uuid/$SIZE"

    fun head(uuid: String): String = "https://mc-heads.net/head/$uuid/$SIZE"
}
