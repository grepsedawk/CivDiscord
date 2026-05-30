package io.github.grepsedawk.civdiscord.velocity.discord

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.RestAction
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class LinkPrompt(
    private val openPrivateChannel: (userId: Long) -> RestAction<PrivateChannel>?,
) {
    private val log = LoggerFactory.getLogger(LinkPrompt::class.java)

    fun notify(userId: Long, fallbackChannel: MessageChannel) {
        val action = openPrivateChannel(userId) ?: return
        val fallback = { err: Throwable -> postChannelFallback(userId, fallbackChannel, err) }
        action.queue(
            { dm -> dm.sendMessage(DM_BODY).queue(null, fallback) },
            fallback,
        )
    }

    private fun postChannelFallback(userId: Long, channel: MessageChannel, cause: Throwable) {
        // JDA 5.2.x: code 50007 (CANNOT_SEND_TO_USER) covers both DMs-closed and
        // DMs-disabled-with-bots. There's no separate OPEN_DM_TO_BOT enum value.
        if (cause is ErrorResponseException && cause.errorResponse != ErrorResponse.CANNOT_SEND_TO_USER) {
            log.warn("DM open/send failed unexpectedly for user {}: {}", userId, cause.message)
        }
        channel.sendMessage("<@$userId> $CHANNEL_BODY").queue(
            { msg: Message -> msg.delete().queueAfter(SELF_DELETE_SECONDS, TimeUnit.SECONDS, null) {} },
            { err -> log.warn("Channel fallback ping failed for user {}: {}", userId, err.message) },
        )
    }

    companion object {
        const val DM_BODY = "Run /discord link in MC first to relay your chat here."
        const val CHANNEL_BODY = "run /discord link in MC first to relay your chat here."
        const val SELF_DELETE_SECONDS = 15L
    }
}
