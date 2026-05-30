package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.concurrent.Executor

class MessageRelayListener(
    private val relays: RelayDao,
    private val bindings: BindingDao,
    private val webhook: WebhookRelay,
    private val linkPrompt: LinkPrompt,
    private val chatRelay: ChatRelay,
    private val worker: Executor,
) : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || event.isWebhookMessage) return
        if (!event.isFromGuild) return
        val channelId = event.channel.idLong
        val relay = relays.findByChannel(channelId) ?: return
        val text = event.message.contentDisplay
        if (text.isBlank()) return

        event.message.delete().queue(null) { /* swallow MANAGE_MESSAGES denial */ }

        val binding = bindings.findByDiscordId(event.author.idLong)
        if (binding == null) {
            linkPrompt.notify(event.author.idLong, event.channel)
            return
        }
        worker.execute {
            webhook.send(channelId, binding.mcName, SkinUrl.head(binding.mcUuid), text)
            chatRelay.fromDiscord(
                channelId = channelId,
                fromDisplay = binding.mcName,
                text = text,
                preComputedGroup = relay.namelayerGroup,
            )
        }
    }
}
