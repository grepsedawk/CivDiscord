package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.LoginLogoutFeedDao
import io.github.grepsedawk.civdiscord.core.feed.LoginLogoutFeedService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Test

class LoginFeedCommandTest {
    private data class Fixture(val cmd: LoginFeedCommand, val svc: LoginLogoutFeedService)

    private fun fixture(): Fixture {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        val svc = LoginLogoutFeedService(LoginLogoutFeedDao(db))
        return Fixture(LoginFeedCommand(svc, guilds), svc)
    }

    private fun event(
        sub: String,
        guildId: Long = 100L,
        channelId: Long = 1001L,
        userId: Long = 5L,
    ): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns sub
        every { e.guild?.idLong } returns guildId
        every { e.channel.idLong } returns channelId
        every { e.user.idLong } returns userId
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        return e
    }

    @Test
    fun `bind on fresh channel writes the binding`() {
        val (cmd, svc) = fixture()
        cmd.handle(event("bind", channelId = 1001L))
        svc.channelId() shouldBe 1001L
    }

    @Test
    fun `bind when already bound names the existing channel and does not change it`() {
        val (cmd, svc) = fixture()
        svc.bind(100L, 1001L, 5L)
        val e = event("bind", channelId = 2002L)
        cmd.handle(e)
        svc.channelId() shouldBe 1001L
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("<#1001>")
        msg.captured.shouldContain("unbind")
    }

    @Test
    fun `unbind clears the binding`() {
        val (cmd, svc) = fixture()
        svc.bind(100L, 1001L, 5L)
        cmd.handle(event("unbind"))
        svc.channelId() shouldBe null
    }

    @Test
    fun `unbind when empty replies not bound`() {
        val (cmd, _) = fixture()
        val e = event("unbind")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("No login/logout feed")
    }

    @Test
    fun `status reports the bound channel`() {
        val (cmd, svc) = fixture()
        svc.bind(100L, 1001L, 5L)
        val e = event("status")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("<#1001>")
    }
}
