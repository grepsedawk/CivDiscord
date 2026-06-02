package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class AdminRunCommandTest {
    private fun eventWith(
        server: String?,
        command: String?,
    ): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.getOption("server") } returns
            server?.let {
                mockk<OptionMapping>().also { om -> every { om.asString } returns it }
            }
        every { e.getOption("command") } returns
            command?.let {
                mockk<OptionMapping>().also { om -> every { om.asString } returns it }
            }
        return e
    }

    @Test
    fun `handle dispatches a ConsoleRequest to the right server`() {
        val sent = mutableListOf<Pair<String, Payload>>()
        val registered = mutableListOf<String>()
        val cmd =
            AdminRunCommand(
                backends = { listOf("citadel", "lobby") },
                dispatch = { server, payload -> sent.add(server to payload) },
                registerPending = { id, _ -> registered.add(id) },
            )
        val e = eventWith("lobby", "say hi")
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.deferReply(true) } returns r
        val successSlot = slot<Consumer<InteractionHook>>()
        every { r.queue(capture(successSlot), any()) } just Runs
        cmd.handle(e)
        successSlot.captured.accept(mockk<InteractionHook>(relaxed = true))
        sent.size shouldBe 1
        sent[0].first shouldBe "lobby"
        (sent[0].second as Payload.ConsoleRequest).command shouldBe "say hi"
        registered.size shouldBe 1
    }

    @Test
    fun `unknown server replies with an error listing known backends`() {
        val sent = mutableListOf<Pair<String, Payload>>()
        val cmd =
            AdminRunCommand(
                backends = { listOf("citadel", "lobby") },
                dispatch = { server, payload -> sent.add(server to payload) },
                registerPending = { _, _ -> },
            )
        val e = eventWith("bogus", "say hi")
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        val msgSlot = slot<String>()
        every { e.reply(capture(msgSlot)) } returns r
        cmd.handle(e)
        sent.size shouldBe 0
        msgSlot.captured shouldContain "Unknown server `bogus`"
        msgSlot.captured shouldContain "`citadel`"
        msgSlot.captured shouldContain "`lobby`"
    }

    @Test
    fun `missing server option replies with Missing server`() {
        val cmd =
            AdminRunCommand(
                backends = { listOf("lobby") },
                dispatch = { _, _ -> error("should not dispatch") },
                registerPending = { _, _ -> },
            )
        val e = eventWith(server = null, command = "say hi")
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        val msgSlot = slot<String>()
        every { e.reply(capture(msgSlot)) } returns r
        every { r.setEphemeral(any()) } returns r
        cmd.handle(e)
        msgSlot.captured shouldBe "Missing server."
    }

    @Test
    fun `missing command option replies with Missing command`() {
        val cmd =
            AdminRunCommand(
                backends = { listOf("lobby") },
                dispatch = { _, _ -> error("should not dispatch") },
                registerPending = { _, _ -> },
            )
        val e = eventWith(server = "lobby", command = null)
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        val msgSlot = slot<String>()
        every { e.reply(capture(msgSlot)) } returns r
        every { r.setEphemeral(any()) } returns r
        cmd.handle(e)
        msgSlot.captured shouldBe "Missing command."
    }

    @Test
    fun `dispatch throws causes hook to be edited with an error and unregisters`() {
        val unregistered = mutableListOf<String>()
        val cmd =
            AdminRunCommand(
                backends = { listOf("lobby") },
                dispatch = { _, _ -> throw RuntimeException("boom") },
                registerPending = { _, _ -> },
                unregisterPending = { id -> unregistered.add(id) },
            )
        val e = eventWith("lobby", "say hi")
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.deferReply(true) } returns r
        val successSlot = slot<Consumer<InteractionHook>>()
        every { r.queue(capture(successSlot), any()) } just Runs
        val hook = mockk<InteractionHook>(relaxed = true)
        val edit = mockk<WebhookMessageEditAction<Message>>(relaxed = true)
        val editMsgSlot = slot<String>()
        every { hook.editOriginal(capture(editMsgSlot)) } returns edit
        cmd.handle(e)
        successSlot.captured.accept(hook)
        editMsgSlot.captured shouldContain "Failed to dispatch console request"
        editMsgSlot.captured shouldContain "boom"
        unregistered.size shouldBe 1
        verify { edit.queue(null, any()) }
    }
}
