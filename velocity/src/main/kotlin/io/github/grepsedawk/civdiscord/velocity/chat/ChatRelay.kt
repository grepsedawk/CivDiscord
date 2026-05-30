package io.github.grepsedawk.civdiscord.velocity.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.RelayDao

class ChatRelay(
    private val relays: RelayDao,
    private val sendToDiscord: (channelId: Long, text: String) -> Unit,
    private val sendChatToDiscord: (channelId: Long, displayName: String, avatarUrl: String, content: String) -> Unit,
    private val sendToMc: (Payload.ChatToMc) -> Unit,
) {
    fun dispatch(
        event: Payload.ChatToDiscord,
        preComputedRouting: List<Long>? = null,
    ) {
        val targets = preComputedRouting ?: relays.findRelaysForGroup(event.namelayerGroup).map { it.discordChannelId }
        for (channelId in targets) {
            sendChatToDiscord(
                channelId,
                "${sanitize(event.fromName)} [${sanitize(event.server)}]",
                "https://mc-heads.net/head/${event.fromUuid}/128",
                sanitize(event.text),
            )
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
