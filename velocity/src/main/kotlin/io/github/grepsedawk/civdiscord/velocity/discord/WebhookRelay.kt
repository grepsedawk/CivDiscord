package io.github.grepsedawk.civdiscord.velocity.discord

import net.dv8tion.jda.api.entities.IncomingWebhookClient
import net.dv8tion.jda.api.entities.WebhookClient
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * One webhook per relay channel, named "CivDiscord Relay". Cached after first resolve.
 *
 * `send` is fire-and-forget: errors are logged, send() does not block the caller's thread.
 * Network calls (`retrieveWebhooks().complete()` / `createWebhook(...).complete()`) happen
 * on whatever thread calls send(), so callers must not be on the JDA event thread; the
 * MessageRelayListener hops to a worker.
 */
class WebhookRelay(
    private val getChannel: (channelId: Long) -> TextChannel?,
) {
    private val log = LoggerFactory.getLogger(WebhookRelay::class.java)
    private val cache = ConcurrentHashMap<Long, IncomingWebhookClient>()
    private val permWarnedChannels = ConcurrentHashMap.newKeySet<Long>()

    fun send(channelId: Long, username: String, avatarUrl: String, content: String) {
        val client = resolve(channelId) ?: return
        try {
            client.sendMessage(content)
                .setUsername(username)
                .setAvatarUrl(avatarUrl)
                .queue(null) { err ->
                    if ((err as? ErrorResponseException)?.errorResponse == ErrorResponse.UNKNOWN_WEBHOOK) {
                        invalidate(channelId)
                    } else {
                        log.warn("Webhook send failed for channel {}: {}", channelId, err.message)
                    }
                }
        } catch (t: Throwable) {
            log.warn("Webhook send threw for channel {}", channelId, t)
        }
    }

    fun invalidate(channelId: Long) {
        cache.remove(channelId)
    }

    private fun resolve(channelId: Long): IncomingWebhookClient? {
        cache[channelId]?.let { return it }
        val ch = getChannel(channelId) ?: return null
        return try {
            cache.computeIfAbsent(channelId) { _ ->
                val existing = ch.retrieveWebhooks().complete().firstOrNull { it.name == NAME }
                val target = existing ?: ch.createWebhook(NAME).complete()
                WebhookClient.createClient(ch.jda, target.url)
            }
        } catch (e: InsufficientPermissionException) {
            if (permWarnedChannels.add(channelId)) {
                log.warn("Missing MANAGE_WEBHOOKS on channel {} — relay falling back to bot send", channelId)
            }
            null
        } catch (t: Throwable) {
            log.warn("Failed to resolve webhook for channel {}", channelId, t)
            null
        }
    }

    companion object {
        const val NAME = "CivDiscord Relay"
    }
}
