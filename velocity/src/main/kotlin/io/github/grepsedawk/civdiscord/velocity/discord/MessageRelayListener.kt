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
    private val permService: NameLayerPermService,
    private val unbind: (channelId: Long, group: String) -> Unit,
    private val writerlessNotifier: WriterlessChannelNotifier,
    private val worker: Executor,
) : ListenerAdapter() {

    private val log = LoggerFactory.getLogger(MessageRelayListener::class.java)
    private val permWarnedChannels = ConcurrentHashMap.newKeySet<Long>()

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || event.isWebhookMessage) return
        if (!event.isFromGuild) return
        val channelId = event.channel.idLong
        val channelRelays = relays.listForChannel(channelId)
        if (channelRelays.isEmpty()) return
        val writer = channelRelays.firstOrNull { it.isWriter }
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

        if (writer == null) {
            writerlessNotifier.notify(channelId, channelRelays.map { it.namelayerGroup })
            return
        }

        val authorBinding = bindings.findByDiscordId(event.author.idLong)
        if (authorBinding == null) {
            linkPrompt.notify(event.author.idLong, event.channel)
            return
        }
        worker.execute {
            val binderBinding = bindings.findByDiscordId(writer.createdBy)
            if (binderBinding == null ||
                !permService.hasPerm(binderBinding.mcUuid, writer.namelayerGroup, NameLayerPermService.READ_CHAT)
            ) {
                unbind(channelId, writer.namelayerGroup)
                return@execute
            }
            webhook.send(channelId, "${authorBinding.mcName} [${writer.namelayerGroup}]", SkinUrl.avatar(authorBinding.mcUuid), text)
            chatRelay.fromDiscord(
                channelId = channelId,
                fromDisplay = authorBinding.mcName,
                fromUuid = authorBinding.mcUuid.toString(),
                text = text,
                preComputedGroup = writer.namelayerGroup,
                discordId = authorBinding.discordId,
            )
        }
    }
}
