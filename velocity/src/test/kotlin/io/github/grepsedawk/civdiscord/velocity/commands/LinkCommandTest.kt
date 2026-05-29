package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.auth.LinkAttemptLimiter
import io.github.grepsedawk.civdiscord.core.auth.LinkService
import io.github.grepsedawk.civdiscord.core.auth.LinkTokenStore
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Test
import java.util.UUID

class LinkCommandTest {

    private data class Fixture(
        val cmd: LinkCommand,
        val tokens: LinkTokenStore,
        val limiter: LinkAttemptLimiter,
        val svc: LinkService,
    )

    private fun fixture(clockNow: () -> Long = { 0L }): Fixture {
        val db = CivDiscordDb.inMemory()
        val tokens = LinkTokenStore(clock = clockNow)
        val bindings = BindingDao(db)
        val svc = LinkService(tokens, bindings)
        val limiter = LinkAttemptLimiter(clock = clockNow)
        return Fixture(LinkCommand(svc, limiter, onLinked = { _, _, _ -> }), tokens, limiter, svc)
    }

    private fun mockEvent(code: String, discordId: Long): SlashCommandInteractionEvent {
        val event = mockk<SlashCommandInteractionEvent>(relaxed = true)
        val opt = mockk<OptionMapping>()
        every { opt.asString } returns code
        every { event.getOption("code") } returns opt
        every { event.user.idLong } returns discordId
        val replyAction = mockk<ReplyCallbackAction>(relaxed = true)
        every { event.reply(any<String>()) } returns replyAction
        every { replyAction.setEphemeral(any()) } returns replyAction
        return event
    }

    @Test
    fun `successful link sends an ephemeral confirmation`() {
        val f = fixture()
        val token = f.tokens.mint(UUID.randomUUID(), "alice")
        val event = mockEvent(token.code, 42L)

        f.cmd.handle(event)

        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldContain("Linked")
    }

    @Test
    fun `bad code replies ephemerally`() {
        val f = fixture()
        val event = mockEvent("BOGUSBOGUSBO", 42L)

        f.cmd.handle(event)

        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldContain("No such code or expired")
    }

    @Test
    fun `mc already linked to another Discord user replies with a pointer to that user`() {
        val db = CivDiscordDb.inMemory()
        val tokens = LinkTokenStore(clock = { 0 })
        val bindings = BindingDao(db)
        val mcUuid = UUID.randomUUID()
        bindings.upsert(111L, mcUuid, "alice")
        val svc = LinkService(tokens, bindings)
        val limiter = LinkAttemptLimiter(clock = { 0L })
        val cmd = LinkCommand(svc, limiter, onLinked = { _, _, _ -> })
        val token = tokens.mint(mcUuid, "alice")
        val event = mockEvent(token.code, 222L)

        cmd.handle(event)

        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldContain("<@111>")
        msg.captured.shouldContain("already linked")
    }

    @Test
    fun `successful link with a backtick in the mc name does not break markdown`() {
        val f = fixture()
        val token = f.tokens.mint(UUID.randomUUID(), "weird`name")
        val event = mockEvent(token.code, 42L)

        f.cmd.handle(event)

        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldNotContain("weird`name")
        msg.captured.count { it == '`' } shouldBe 2
    }

    @Test
    fun `locked-out caller is rejected without consulting LinkService`() {
        val db = CivDiscordDb.inMemory()
        val tokens = LinkTokenStore(clock = { 0 })
        val bindings = BindingDao(db)
        val svc = mockk<LinkService>()
        val limiter = LinkAttemptLimiter(clock = { 0L })
        val cmd = LinkCommand(svc, limiter, onLinked = { _, _, _ -> })
        repeat(5) { limiter.recordFailure(42L) }
        val event = mockEvent("ANYCODEABCDE", 42L)

        cmd.handle(event)

        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldContain("Too many failed attempts")
        verify(exactly = 0) { svc.redeem(any(), any()) }
        // Silence unused-warning for db/bindings/tokens
        db.toString()
        bindings.toString()
        tokens.toString()
    }

    @Test
    fun `five bad code attempts lock the user out`() {
        val f = fixture()
        repeat(5) {
            val event = mockEvent("BOGUSCODE$it" + "X", 42L)
            f.cmd.handle(event)
        }
        f.limiter.isLockedOut(42L) shouldBe true
    }

    @Test
    fun `successful link clears prior failures`() {
        val f = fixture()
        repeat(4) {
            val event = mockEvent("BOGUSCODE$it" + "X", 42L)
            f.cmd.handle(event)
        }
        val token = f.tokens.mint(UUID.randomUUID(), "alice")
        val ok = mockEvent(token.code, 42L)
        f.cmd.handle(ok)
        f.limiter.isLockedOut(42L) shouldBe false
        f.limiter.recordFailure(42L) shouldBe false
    }
}
