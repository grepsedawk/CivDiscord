package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.db.Binding
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executor

class MessageRelayListenerTest {

    private val groupName = "grepsedawk"
    private val channelId = 9000L
    private val mcUuid = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")
    private val mcName = "grepsedawk"
    private val discordId = 42L

    private data class Fixture(
        val listener: MessageRelayListener,
        val webhook: WebhookRelay,
        val linkPrompt: LinkPrompt,
        val chatRelay: ChatRelay,
    )

    private fun event(
        isBot: Boolean = false,
        isWebhook: Boolean = false,
        fromGuild: Boolean = true,
        chId: Long = channelId,
        authorId: Long = discordId,
        content: String = "hello",
    ): MessageReceivedEvent {
        val e = mockk<MessageReceivedEvent>(relaxed = true)
        val author = mockk<User>(relaxed = true)
        every { author.isBot } returns isBot
        every { author.idLong } returns authorId
        every { e.author } returns author
        every { e.isWebhookMessage } returns isWebhook
        every { e.isFromGuild } returns fromGuild
        val channel = mockk<MessageChannelUnion>(relaxed = true)
        every { channel.idLong } returns chId
        every { e.channel } returns channel
        val msg = mockk<Message>(relaxed = true)
        every { msg.contentDisplay } returns content
        every { e.message } returns msg
        every { msg.delete() } returns mockk<AuditableRestAction<Void>>(relaxed = true)
        return e
    }

    private val sameThread = Executor { it.run() }

    private fun setup(linked: Boolean): Fixture {
        val relays = mockk<RelayDao>(relaxed = true)
        every { relays.findByChannel(channelId) } returns
            Relay(
                id = 1, guildId = 0L, namelayerGroup = groupName,
                discordChannelId = channelId, showSnitches = false,
                chatFormat = null, createdBy = 0L, createdAt = 0L,
            )
        val bindings = mockk<BindingDao>(relaxed = true)
        every { bindings.findByDiscordId(discordId) } returns
            if (linked) Binding(discordId, mcUuid, mcName, 0L) else null
        val webhook = mockk<WebhookRelay>(relaxed = true)
        val linkPrompt = mockk<LinkPrompt>(relaxed = true)
        val chatRelay = mockk<ChatRelay>(relaxed = true)
        return Fixture(
            MessageRelayListener(relays, bindings, webhook, linkPrompt, chatRelay, sameThread),
            webhook,
            linkPrompt,
            chatRelay,
        )
    }

    @Test
    fun `bot message is ignored`() {
        val f = setup(linked = true)
        f.listener.onMessageReceived(event(isBot = true))
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { f.linkPrompt.notify(any(), any()) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any()) }
    }

    @Test
    fun `webhook message is ignored (no loop)`() {
        val f = setup(linked = true)
        f.listener.onMessageReceived(event(isWebhook = true))
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any()) }
    }

    @Test
    fun `non-relay channel is ignored`() {
        val relays = mockk<RelayDao>(relaxed = true)
        every { relays.findByChannel(any()) } returns null
        val bindings = mockk<BindingDao>(relaxed = true)
        val webhook = mockk<WebhookRelay>(relaxed = true)
        val linkPrompt = mockk<LinkPrompt>(relaxed = true)
        val chatRelay = mockk<ChatRelay>(relaxed = true)
        val l = MessageRelayListener(relays, bindings, webhook, linkPrompt, chatRelay, sameThread)
        l.onMessageReceived(event())
        verify(exactly = 0) { webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { linkPrompt.notify(any(), any()) }
        verify(exactly = 0) { chatRelay.fromDiscord(any(), any(), any(), any()) }
    }

    @Test
    fun `linked user deletes, webhooks, and bridges`() {
        val f = setup(linked = true)
        val e = event(content = "hi")
        val msg = e.message
        f.listener.onMessageReceived(e)
        verify(exactly = 1) { msg.delete() }
        verify(exactly = 1) {
            f.webhook.send(
                channelId,
                mcName,
                SkinUrl.head(mcUuid),
                "hi",
            )
        }
        verify(exactly = 1) {
            f.chatRelay.fromDiscord(
                channelId = channelId,
                fromDisplay = mcName,
                text = "hi",
                preComputedGroup = groupName,
            )
        }
        verify(exactly = 0) { f.linkPrompt.notify(any(), any()) }
    }

    @Test
    fun `unlinked user deletes and prompts to link`() {
        val f = setup(linked = false)
        val e = event()
        val msg = e.message
        val channel = e.channel
        f.listener.onMessageReceived(e)
        verify(exactly = 1) { msg.delete() }
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 1) { f.linkPrompt.notify(discordId, channel) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any()) }
    }

    @Test
    fun `attachment-only message is left alone`() {
        val f = setup(linked = true)
        val e = event(content = "")
        val msg = e.message
        f.listener.onMessageReceived(e)
        verify(exactly = 0) { msg.delete() }
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any()) }
        verify(exactly = 0) { f.linkPrompt.notify(any(), any()) }
    }
}
