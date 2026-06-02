package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.admin.AdminService
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

class AdminUserCommandTest {
    private fun fixture(): Pair<AdminUserCommand, BindingDao> {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        return AdminUserCommand(AdminService(bindings)) to bindings
    }

    private fun event(sub: String, targetId: Long): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns sub
        val opt = mockk<OptionMapping>()
        every { opt.asLong } returns targetId
        every { e.getOption("discord-user") } returns opt
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        return e
    }

    @Test
    fun `view returns mc binding info for a linked user`() {
        val (cmd, bindings) = fixture()
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        val e = event("view", 42L)
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("alice")
    }

    @Test
    fun `view returns 'not linked' for absent`() {
        val (cmd, _) = fixture()
        val e = event("view", 42L)
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("not linked")
    }

    @Test
    fun `unlink removes the binding`() {
        val (cmd, bindings) = fixture()
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        val e = event("unlink", 42L)
        cmd.handle(e)
        bindings.findByDiscordId(42L) shouldBe null
    }

    @Test
    fun `unlink on a not-linked user replies with not-linked`() {
        val (cmd, _) = fixture()
        val e = event("unlink", 42L)
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("was not linked")
    }

    @Test
    fun `unknown subcommand replies with the unknown-subcommand error`() {
        val (cmd, _) = fixture()
        val e = event("wat", 42L)
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("Unknown subcommand")
    }

    @Test
    fun `missing discord-user option replies with Missing discord-user`() {
        val (cmd, _) = fixture()
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.getOption("discord-user") } returns null
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("Missing discord-user")
    }

    @Test
    fun `view with a backtick in the mc name does not break markdown`() {
        val (cmd, bindings) = fixture()
        bindings.upsert(42L, UUID.randomUUID(), "weird`name")
        val e = event("view", 42L)
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured shouldNotContain "weird`name"
        msg.captured.count { it == '`' } shouldBe 4
    }
}
