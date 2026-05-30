package io.github.grepsedawk.civdiscord.velocity.snitch

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.core.text.MarkdownSafe

/**
 * Fans a SnitchHit out to every relay channel that subscribes to the snitch's NameLayer group
 * AND has show_snitches enabled. Pure-Kotlin so it's testable without JDA.
 */
class SnitchRelay(
    private val relays: RelayDao,
    private val sendToDiscord: (channelId: Long, text: String) -> Unit,
) {
    fun dispatch(hit: Payload.SnitchHit) {
        val targets = relays.findRelaysForGroup(hit.namelayerGroup).filter { it.showSnitches }
        if (targets.isEmpty()) return
        val rendered = render(hit)
        for (r in targets) sendToDiscord(r.discordChannelId, rendered)
    }

    private fun render(hit: Payload.SnitchHit): String {
        val kind = when (hit.kind.uppercase()) {
            "ENTER" -> "hit"
            "LOGIN" -> "login"
            "LOGOUT" -> "logout"
            else -> MarkdownSafe.text(hit.kind.lowercase())
        }
        val intruder = MarkdownSafe.code(hit.intruderUuid)
        val name = MarkdownSafe.code(hit.snitchName.ifBlank { "(unnamed)" })
        val server = MarkdownSafe.code(hit.server)
        return "**SNITCH** [$kind] `$intruder` at `${hit.x} ${hit.y} ${hit.z}` (`$name`) [`$server`]"
    }
}
