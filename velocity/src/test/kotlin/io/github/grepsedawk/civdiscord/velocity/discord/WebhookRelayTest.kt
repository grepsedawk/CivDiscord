package io.github.grepsedawk.civdiscord.velocity.discord

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.WebhookAction
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
        every { created.name } returns "CivDiscord Relay"
        every { created.url } returns "https://discord.com/api/webhooks/2/def"
        every { createAction.complete() } returns created
        return ch
    }

    @Test
    fun `cache miss with no existing webhook creates one`() {
        val ch = channel("c", existingWebhookName = null)
        val relay = WebhookRelay(getChannel = { id -> if (id == 100L) ch else null })

        relay.send(100L, "grepsedawk", "https://mc-heads.net/head/uuid/128", "hi")

        verify(exactly = 1) { ch.createWebhook("CivDiscord Relay") }
    }

    @Test
    fun `cache miss with existing webhook reuses it`() {
        val ch = channel("c", existingWebhookName = "CivDiscord Relay")
        val relay = WebhookRelay(getChannel = { id -> if (id == 100L) ch else null })

        relay.send(100L, "grepsedawk", "url", "hi")

        verify(exactly = 0) { ch.createWebhook(any()) }
    }

    @Test
    fun `cache hit reuses webhook client`() {
        val ch = channel("c", existingWebhookName = "CivDiscord Relay")
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
        val ch = channel("c", existingWebhookName = "CivDiscord Relay")
        val relay = WebhookRelay(getChannel = { id -> if (id == 100L) ch else null })

        relay.send(100L, "a", "u", "hi")
        relay.invalidate(100L)
        relay.send(100L, "a", "u", "hi")

        verify(exactly = 2) { ch.retrieveWebhooks() }
    }
}
