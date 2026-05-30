package io.github.grepsedawk.civdiscord.velocity.discord

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.dv8tion.jda.api.entities.IncomingWebhookClient
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.WebhookClient
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.WebhookAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageRequest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebhookRelayTest {

    private fun channel(name: String, existingWebhookName: String? = null): TextChannel {
        val ch = mockk<TextChannel>(relaxed = true)
        val webhooks: MutableList<Webhook> = mutableListOf()
        if (existingWebhookName != null) {
            val wh = mockk<Webhook>(relaxed = true)
            every { wh.name } returns existingWebhookName
            every { wh.url } returns "https://discord.com/api/webhooks/1/abc"
            webhooks.add(wh)
        }
        val retrieve = mockk<RestAction<MutableList<Webhook>>>(relaxed = true)
        every { retrieve.complete() } returns webhooks
        every { ch.retrieveWebhooks() } returns retrieve
        val createAction = mockk<WebhookAction>(relaxed = true)
        every { ch.createWebhook(any()) } returns createAction
        val created = mockk<Webhook>(relaxed = true)
        every { created.name } returns WebhookRelay.NAME
        every { created.url } returns "https://discord.com/api/webhooks/2/def"
        every { createAction.complete() } returns created
        return ch
    }

    @Test
    fun `cache miss with no existing webhook creates one`() {
        val ch = channel("c", existingWebhookName = null)
        val relay = WebhookRelay(getChannel = { id -> if (id == 100L) ch else null })

        relay.send(100L, "grepsedawk", "https://mc-heads.net/head/uuid/128", "hi")

        verify(exactly = 1) { ch.createWebhook(WebhookRelay.NAME) }
    }

    @Test
    fun `cache miss with existing webhook reuses it`() {
        val ch = channel("c", existingWebhookName = WebhookRelay.NAME)
        val relay = WebhookRelay(getChannel = { id -> if (id == 100L) ch else null })

        relay.send(100L, "grepsedawk", "url", "hi")

        verify(exactly = 0) { ch.createWebhook(any()) }
    }

    @Test
    fun `cache hit reuses webhook client`() {
        val ch = channel("c", existingWebhookName = WebhookRelay.NAME)
        val relay = WebhookRelay(getChannel = { id -> if (id == 100L) ch else null })

        relay.send(100L, "a", "u", "hi")
        relay.send(100L, "a", "u", "hi again")

        verify(exactly = 1) { ch.retrieveWebhooks() }
    }

    @Test
    fun `missing channel is a no-op`() {
        val relay = WebhookRelay(getChannel = { null })
        // Should not throw
        relay.send(999L, "a", "u", "hi")
    }

    @Test
    fun `invalidate clears cache for channel`() {
        val ch = channel("c", existingWebhookName = WebhookRelay.NAME)
        val relay = WebhookRelay(getChannel = { id -> if (id == 100L) ch else null })

        relay.send(100L, "a", "u", "hi")
        relay.invalidate(100L)
        relay.send(100L, "a", "u", "hi")

        verify(exactly = 2) { ch.retrieveWebhooks() }
    }

    @Test
    fun `send disables all mentions on the webhook message`() {
        // Webhooks don't inherit the original author's MENTION_EVERYONE permission, so the
        // literal strings "@everyone" / "@here" Discord re-parses on send would otherwise let
        // any linked user ping the guild via relay.
        val ch = channel("c", existingWebhookName = WebhookRelay.NAME)
        val incomingClient = mockk<IncomingWebhookClient>(relaxed = true)
        val sendAction = mockk<WebhookMessageCreateAction<Message>>(relaxed = true)
        every { incomingClient.sendMessage(any<String>()) } returns sendAction
        every { sendAction.setUsername(any()) } returns sendAction
        every { sendAction.setAvatarUrl(any()) } returns sendAction
        every { sendAction.setAllowedMentions(any()) } returns sendAction
        mockkStatic(WebhookClient::class)
        try {
            every { WebhookClient.createClient(any(), any<String>()) } returns incomingClient
            val relay = WebhookRelay(getChannel = { id -> if (id == 100L) ch else null })

            relay.send(100L, "grepsedawk", "url", "@everyone hi")

            verify { incomingClient.sendMessage("@everyone hi") }
            verify { sendAction.setAllowedMentions(emptyList<Message.MentionType>()) }
        } finally {
            unmockkStatic(WebhookClient::class)
        }
    }

    @Test
    fun `JDA-wide default mentions are empty after configuring once`() {
        // Defense-in-depth: JdaFactory sets MessageRequest.setDefaultMentions(emptyList()) at
        // startup. Verify the static is configurable to an empty set; any code path that forgets
        // setAllowedMentions still won't ping @everyone/@here.
        MessageRequest.setDefaultMentions(emptyList<Message.MentionType>())
        val data: MessageCreateData =
            MessageCreateBuilder().setContent("@everyone hello").build()
        assertTrue(data.allowedMentions.isEmpty())
    }

    @Test
    fun `webhook name complies with Discord username rules`() {
        // Discord rejects webhook names containing "discord" or "clyde" (error 50035
        // USERNAME_INVALID_CONTAINS). If this ever regresses, every channel will silently
        // fail to ever create its webhook — and users' messages will vanish.
        val lower = WebhookRelay.NAME.lowercase()
        check(!lower.contains("discord")) { "Webhook name must not contain 'discord': ${WebhookRelay.NAME}" }
        check(!lower.contains("clyde")) { "Webhook name must not contain 'clyde': ${WebhookRelay.NAME}" }
    }
}
