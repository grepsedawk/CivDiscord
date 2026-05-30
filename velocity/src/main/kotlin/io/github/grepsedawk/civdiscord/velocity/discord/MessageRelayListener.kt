package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

class MessageRelayListener(
    private val relays: RelayDao,
    private val bindings: BindingDao,
    private val webhook: WebhookRelay,
    private val linkPrompt: LinkPrompt,
    private val chatRelay: ChatRelay,
    private val worker: Executor,
) : ListenerAdapter() {

    private val log = LoggerFactory.getLogger(MessageRelayListener::class.java)
    private val permWarnedChannels = ConcurrentHashMap.newKeySet<Long>()

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || event.isWebhookMessage) return
        if (!event.isFromGuild) return
        val channelId = event.channel.idLong
        val relay = relays.findByChannel(channelId) ?: return
        val text = event.message.contentDisplay
        if (text.isBlank()) return

        event.message.delete().queue(null) { err ->
            val denied = err is PermissionException ||
                (err as? ErrorResponseException)?.errorResponse == ErrorResponse.MISSING_PERMISSIONS
            if (denied && permWarnedChannels.add(channelId)) {
                log.warn(
                    "Missing MANAGE_MESSAGES on channel {} — user messages will remain visible alongside the webhook re-post until the bot is granted the permission",
                    channelId,
                )
            }
        }

        val binding = bindings.findByDiscordId(event.author.idLong)
        if (binding == null) {
            linkPrompt.notify(event.author.idLong, event.channel)
            return
        }
        worker.execute {
            webhook.send(channelId, "${binding.mcName} [${relay.namelayerGroup}]", SkinUrl.head(binding.mcUuid), text)
            chatRelay.fromDiscord(
                channelId = channelId,
                fromDisplay = binding.mcName,
                fromUuid = binding.mcUuid.toString(),
                text = text,
                preComputedGroup = relay.namelayerGroup,
                discordId = binding.discordId,
            )
        }
    }
}
