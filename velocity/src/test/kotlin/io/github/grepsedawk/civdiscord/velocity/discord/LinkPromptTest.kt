package io.github.grepsedawk.civdiscord.velocity.discord

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class LinkPromptTest {

    @Test
    fun `dm success delivers DM, no channel fallback`() {
        val dm = mockk<PrivateChannel>(relaxed = true)
        val sendAction = mockk<MessageCreateAction>(relaxed = true)
        every { dm.sendMessage(any<String>()) } returns sendAction
        val open = mockk<RestAction<PrivateChannel>>(relaxed = true)
        every { open.queue(any(), any()) } answers {
            firstArg<java.util.function.Consumer<PrivateChannel>>().accept(dm)
        }

        val ch = mockk<MessageChannel>(relaxed = true)
        val prompt = LinkPrompt(openPrivateChannel = { open })

        prompt.notify(userId = 42L, fallbackChannel = ch)

        verify(exactly = 1) { dm.sendMessage(match<String> { it.contains("/discord link") }) }
        verify(exactly = 0) { ch.sendMessage(any<String>()) }
    }

    @Test
    fun `dm failure triggers channel ping with 15s self-delete`() {
        val open = mockk<RestAction<PrivateChannel>>(relaxed = true)
        val cannotDm = ErrorResponseException.create(
            ErrorResponse.CANNOT_SEND_TO_USER,
            mockk(relaxed = true),
        )
        every { open.queue(any(), any()) } answers {
            secondArg<java.util.function.Consumer<Throwable>>().accept(cannotDm)
        }
        val ch = mockk<MessageChannel>(relaxed = true)
        val channelSend = mockk<MessageCreateAction>(relaxed = true)
        every { ch.sendMessage(any<String>()) } returns channelSend

        val sentMessage = mockk<Message>(relaxed = true)
        val deleteAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { sentMessage.delete() } returns deleteAction

        every { channelSend.queue(any(), any()) } answers {
            firstArg<java.util.function.Consumer<Message>>().accept(sentMessage)
        }

        val prompt = LinkPrompt(openPrivateChannel = { open })
        prompt.notify(userId = 42L, fallbackChannel = ch)

        verify(exactly = 1) { ch.sendMessage(match<String> { it.contains("<@42>") && it.contains("/discord link") }) }
        verify(exactly = 1) { channelSend.queue(any(), any()) }
        verify(exactly = 1) { sentMessage.delete() }
        verify(exactly = 1) {
            deleteAction.queueAfter(15L, TimeUnit.SECONDS, null, any<java.util.function.Consumer<Throwable>>())
        }
    }
}
