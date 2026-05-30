package io.github.grepsedawk.civdiscord.velocity.snitch

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.bridge.RateLimitedLogger
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.core.text.MarkdownSafe
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Fans a SnitchHit out to every relay channel that subscribes to the snitch's NameLayer group
 * AND has show_snitches enabled. Pure-Kotlin so it's testable without JDA.
 */
class SnitchRelay(
    private val relays: RelayDao,
    private val sendToDiscord: (channelId: Long, text: String) -> Unit,
    private val logger: Logger = LoggerFactory.getLogger(SnitchRelay::class.java),
) {
    private val noRelayLog = RateLimitedLogger(logger, TimeUnit.MINUTES.toNanos(5))
    private val allDisabledLog = RateLimitedLogger(logger, TimeUnit.MINUTES.toNanos(5))

    fun dispatch(hit: Payload.SnitchHit) {
        logger.info(
            "SnitchRelay: received hit kind={} group='{}' server={} snitch='{}'",
            hit.kind,
            hit.namelayerGroup,
            hit.server,
            hit.snitchName,
        )
        val matched = relays.findRelaysForGroup(hit.namelayerGroup)
        if (matched.isEmpty()) {
            noRelayLog.warn(
                "SnitchRelay: no relays bound to NameLayer group '${hit.namelayerGroup}' — drop. " +
                    "Hint: /relay bind <group> in the target channel.",
            )
            return
        }
        val targets = matched.filter { it.showSnitches }
        if (targets.isEmpty()) {
            allDisabledLog.warn(
                "SnitchRelay: ${matched.size} relay(s) match group '${hit.namelayerGroup}' but none have " +
                    "show_snitches=true — drop. Hint: /relay set show-snitches true.",
            )
            return
        }
        logger.info(
            "SnitchRelay: fanned out hit to {} channel(s) for group '{}'",
            targets.size,
            hit.namelayerGroup,
        )
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
        val intruder = MarkdownSafe.code(hit.intruderName?.takeIf { it.isNotBlank() } ?: hit.intruderUuid)
        val name = MarkdownSafe.code(hit.snitchName.ifBlank { "(unnamed)" })
        val server = MarkdownSafe.code(hit.server)
        return "**SNITCH** [$kind] `$intruder` at `${hit.x} ${hit.y} ${hit.z}` (`$name`) [`$server`]"
    }
}
