package io.github.grepsedawk.civdiscord.velocity.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao

class ChatRelay(
    private val relays: RelayDao,
    private val sendToDiscord: (channelId: Long, text: String) -> Unit,
    private val sendChatToDiscord: (channelId: Long, displayName: String, avatarUrl: String, content: String) -> Unit,
    private val sendToMc: (Payload.ChatToMc) -> Unit,
    private val rateLimiter: ChatRateLimiter? = null,
    private val maxTextLength: Int = 256,
) {
    fun dispatch(
        event: Payload.ChatToDiscord,
        preComputedRouting: List<Relay>? = null,
    ) {
        val targets = preComputedRouting ?: relays.findRelaysForGroup(event.namelayerGroup)
        val safeName = sanitize(event.fromName)
        val safeGroup = sanitize(event.namelayerGroup)
        val safeServer = sanitize(event.server)
        val safeText = sanitize(event.text)
        for (relay in targets) {
            val content = render(relay.chatFormat ?: DEFAULT_FORMAT, safeName, safeServer, safeText, safeGroup)
            sendChatToDiscord(
                relay.discordChannelId,
                "$safeName [$safeGroup]",
                "https://mc-heads.net/head/${event.fromUuid}/128",
                content,
            )
        }
    }

    private fun render(
        template: String,
        name: String,
        server: String,
        text: String,
        group: String,
    ): String = PLACEHOLDER_REGEX.replace(template) { m ->
        when (m.groupValues[1]) {
            "name" -> name
            "server" -> server
            "text" -> text
            "group" -> group
            else -> m.value
        }
    }

    private fun sanitize(input: String): String = input
        .replace("`", "")
        .replace(Regex("([*_~|>\\\\])"), "\\\\$1")
        .replace("@everyone", "@​everyone")
        .replace("@here", "@​here")
        .replace("<@", "<​@")
        .replace("<#", "<​#")

    fun fromDiscord(
        channelId: Long,
        fromDisplay: String,
        fromUuid: String?,
        text: String,
        preComputedGroup: String? = null,
        discordId: Long? = null,
    ) {
        val group = preComputedGroup ?: relays.findByChannel(channelId)?.namelayerGroup ?: return
        if (discordId != null && rateLimiter != null && !rateLimiter.tryAcquire(discordId)) return
        val cleaned = sanitizeForMc(text)
        if (cleaned.isEmpty()) return
        sendToMc(
            Payload.ChatToMc(
                server = "*",
                namelayerGroup = group,
                from = fromDisplay,
                text = cleaned,
                fromUuid = fromUuid,
            ),
        )
    }

    private fun sanitizeForMc(input: String): String {
        val collapsed = CONTROL_CHARS.replace(input, " ")
        return if (collapsed.length > maxTextLength) collapsed.substring(0, maxTextLength) else collapsed
    }

    companion object {
        const val DEFAULT_FORMAT: String = "**{name}** [`{server}`]: {text}"
        val ALLOWED_PLACEHOLDERS: Set<String> = setOf("name", "server", "text", "group")

        private val PLACEHOLDER_REGEX = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)}")
        private val CONTROL_CHARS = Regex("[\\u0000-\\u001f]")

        fun unknownPlaceholders(template: String): List<String> = PLACEHOLDER_REGEX.findAll(template)
            .map { it.groupValues[1] }
            .filter { it !in ALLOWED_PLACEHOLDERS }
            .toList()
    }
}
