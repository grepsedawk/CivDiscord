package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.junit.jupiter.api.Test

class MessageRelayListenerTest {

    private fun relay(): Pair<ChatRelay, ChatRelay> {
        val real = ChatRelay(
            relays = RelayDao(CivDiscordDb.inMemory()),
            sendToDiscord = { _, _ -> },
            sendToMc = { _ -> },
        )
        val spy = mockk<ChatRelay>(relaxed = true)
        return real to spy
    }

    @Test
    fun `forwards a human guild message to ChatRelay fromDiscord`() {
        val (_, spy) = relay()
        val listener = MessageRelayListener(spy)
        val event = mockk<MessageReceivedEvent>(relaxed = true)
        every { event.author.isBot } returns false
        every { event.isFromGuild } returns true
        every { event.channel.idLong } returns 1001L
        every { event.member?.effectiveName } returns "alice"
        every { event.message.contentDisplay } returns "hello"

        listener.onMessageReceived(event)

        verify(exactly = 1) {
            spy.fromDiscord(channelId = 1001L, fromDisplay = "alice", text = "hello")
        }
    }

    @Test
    fun `falls back to author effectiveName when member is null`() {
        val (_, spy) = relay()
        val listener = MessageRelayListener(spy)
        val event = mockk<MessageReceivedEvent>(relaxed = true)
        every { event.author.isBot } returns false
        every { event.isFromGuild } returns true
        every { event.channel.idLong } returns 2002L
        every { event.member } returns null
        every { event.author.effectiveName } returns "guest"
        every { event.message.contentDisplay } returns "yo"

        listener.onMessageReceived(event)

        verify(exactly = 1) {
            spy.fromDiscord(channelId = 2002L, fromDisplay = "guest", text = "yo")
        }
    }

    @Test
    fun `ignores bot messages`() {
        val (_, spy) = relay()
        val listener = MessageRelayListener(spy)
        val event = mockk<MessageReceivedEvent>(relaxed = true)
        every { event.author.isBot } returns true

        listener.onMessageReceived(event)

        verify(exactly = 0) { spy.fromDiscord(any(), any(), any(), any()) }
    }

    @Test
    fun `ignores DMs`() {
        val (_, spy) = relay()
        val listener = MessageRelayListener(spy)
        val event = mockk<MessageReceivedEvent>(relaxed = true)
        every { event.author.isBot } returns false
        every { event.isFromGuild } returns false

        listener.onMessageReceived(event)

        verify(exactly = 0) { spy.fromDiscord(any(), any(), any(), any()) }
    }

    @Test
    fun `extends ListenerAdapter so JDA dispatches the event`() {
        val (_, spy) = relay()
        val listener = MessageRelayListener(spy)
        net.dv8tion.jda.api.hooks.ListenerAdapter::class.java
            .isAssignableFrom(listener::class.java) shouldBe true
    }
}
