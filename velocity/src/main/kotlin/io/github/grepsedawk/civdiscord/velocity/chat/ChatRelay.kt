package io.github.grepsedawk.civdiscord.velocity.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.velocity.discord.NameLayerPermService
import io.github.grepsedawk.civdiscord.velocity.discord.SkinUrl

class ChatRelay(
    private val relays: RelayDao,
    private val bindings: BindingDao,
    private val permService: NameLayerPermService,
    private val sendToDiscord: (channelId: Long, text: String) -> Unit,
    private val sendChatToDiscord: (channelId: Long, displayName: String, avatarUrl: String, content: String) -> Unit,
    private val sendToMc: (Payload.ChatToMc) -> Unit,
    private val unbind: (channelId: Long, group: String) -> Unit,
    private val onWriterless: (channelId: Long, bound: List<String>) -> Unit = { _, _ -> },
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
            val binderBinding = bindings.findByDiscordId(relay.createdBy)
            if (binderBinding == null || !permService.hasPerm(binderBinding.mcUuid, relay.namelayerGroup, NameLayerPermService.READ_CHAT)) {
                unbind(relay.discordChannelId, relay.namelayerGroup)
                continue
            }
            val content = render(relay.chatFormat ?: DEFAULT_FORMAT, safeName, safeServer, safeText, safeGroup)
            sendChatToDiscord(
                relay.discordChannelId,
                "$safeName [$safeGroup]",
                SkinUrl.avatar(event.fromUuid),
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
        val channelBindings = relays.listForChannel(channelId)
        if (channelBindings.isEmpty()) return
        val writer = channelBindings.firstOrNull { it.isWriter }
        if (writer == null) {
            onWriterless(channelId, channelBindings.map { it.namelayerGroup })
            return
        }
        val binderBinding = this.bindings.findByDiscordId(writer.createdBy)
        if (binderBinding == null || !permService.hasPerm(binderBinding.mcUuid, writer.namelayerGroup, NameLayerPermService.READ_CHAT)) {
            unbind(channelId, writer.namelayerGroup)
            return
        }
        val group = preComputedGroup ?: writer.namelayerGroup
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
        val others = relays.findRelaysForGroup(writer.namelayerGroup)
            .filter { it.discordChannelId != channelId }
        if (others.isNotEmpty()) {
            dispatch(
                Payload.ChatToDiscord(
                    server = "*",
                    fromUuid = fromUuid ?: "00000000-0000-0000-0000-000000000000",
                    fromName = fromDisplay,
                    namelayerGroup = writer.namelayerGroup,
                    text = cleaned,
                ),
                preComputedRouting = others,
            )
        }
    }

    private fun sanitizeForMc(input: String): String {
        val collapsed = CONTROL_CHARS.replace(input, " ")
        return if (collapsed.length > maxTextLength) collapsed.substring(0, maxTextLength) else collapsed
    }

    companion object {
        const val DEFAULT_FORMAT: String = "{text}"
        val ALLOWED_PLACEHOLDERS: Set<String> = setOf("name", "server", "text", "group")

        private val PLACEHOLDER_REGEX = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)}")
        private val CONTROL_CHARS = Regex("[\\u0000-\\u001f]")

        fun unknownPlaceholders(template: String): List<String> = PLACEHOLDER_REGEX.findAll(template)
            .map { it.groupValues[1] }
            .filter { it !in ALLOWED_PLACEHOLDERS }
            .toList()
    }
}
