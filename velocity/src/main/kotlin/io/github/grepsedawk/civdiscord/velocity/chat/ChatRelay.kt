package io.github.grepsedawk.civdiscord.velocity.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao

class ChatRelay(
    private val relays: RelayDao,
    private val sendToDiscord: (channelId: Long, text: String) -> Unit,
    private val sendToMc: (Payload.ChatToMc) -> Unit,
) {
    fun dispatch(
        event: Payload.ChatToDiscord,
        preComputedRouting: List<Long>? = null,
    ) {
        if (preComputedRouting != null) {
            val rendered = render(null, event)
            for (channel in preComputedRouting) sendToDiscord(channel, rendered)
            return
        }
        for (relay in relays.findRelaysForGroup(event.namelayerGroup)) {
            sendToDiscord(relay.discordChannelId, render(relay, event))
        }
    }

    private fun sanitize(input: String): String =
        input
            .replace("`", "")
            .replace(Regex("([*_~|>\\\\])"), "\\\\$1")
            .replace("@everyone", "@​everyone")
            .replace("@here", "@​here")
            .replace("<@", "<​@")
            .replace("<#", "<​#")

    private fun render(relay: Relay?, event: Payload.ChatToDiscord): String {
        val template = relay?.chatFormat ?: DEFAULT_FORMAT
        return template
            .replace("{name}", sanitize(event.fromName))
            .replace("{server}", sanitize(event.server))
            .replace("{text}", sanitize(event.text))
            .replace("{group}", sanitize(event.namelayerGroup))
    }

    fun fromDiscord(
        channelId: Long,
        fromDisplay: String,
        text: String,
        preComputedGroup: String? = null,
    ) {
        val group = preComputedGroup ?: relays.findByChannel(channelId)?.namelayerGroup ?: return
        sendToMc(Payload.ChatToMc(server = "*", namelayerGroup = group, from = fromDisplay, text = text))
    }

    companion object {
        const val DEFAULT_FORMAT: String = "**{name}** [`{server}`]: {text}"
        val ALLOWED_PLACEHOLDERS: Set<String> = setOf("name", "server", "text", "group")

        private val PLACEHOLDER_REGEX = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)}")

        fun unknownPlaceholders(template: String): List<String> =
            PLACEHOLDER_REGEX.findAll(template)
                .map { it.groupValues[1] }
                .filter { it !in ALLOWED_PLACEHOLDERS }
                .toList()
    }
}
