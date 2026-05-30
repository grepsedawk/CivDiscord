package io.github.grepsedawk.civdiscord.velocity.commands

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Test

class SlashCommandDispatcherTest {
    private val homeGuildId = 11111L
    private val otherGuildId = 22222L

    private fun dispatcher(
        link: LinkCommand = mockk(relaxed = true),
        me: MeCommand = mockk(relaxed = true),
        relay: RelayCommand = mockk(relaxed = true),
        adminUser: AdminUserCommand = mockk(relaxed = true),
        adminGuild: AdminGuildCommand = mockk(relaxed = true),
        adminRun: AdminRunCommand = mockk(relaxed = true),
        backends: () -> List<String> = { emptyList() },
    ): SixHandlers {
        val d = SlashCommandDispatcher(
            homeGuildId = homeGuildId,
            restErrorHandler = ErrorHandler(),
            link = link,
            me = me,
            relay = relay,
            adminUser = adminUser,
            adminGuild = adminGuild,
            adminRun = adminRun,
            backends = backends,
        )
        return SixHandlers(d, link, me, relay, adminUser, adminGuild, adminRun)
    }

    private data class SixHandlers(
        val dispatcher: SlashCommandDispatcher,
        val link: LinkCommand,
        val me: MeCommand,
        val relay: RelayCommand,
        val adminUser: AdminUserCommand,
        val adminGuild: AdminGuildCommand,
        val adminRun: AdminRunCommand,
    )

    private fun adminEvent(
        guildId: Long?,
        subcommandGroup: String? = null,
        subcommandName: String? = null,
    ): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.name } returns "admin"
        every { e.subcommandGroup } returns subcommandGroup
        every { e.subcommandName } returns subcommandName
        every { e.guild } returns guildId?.let {
            mockk<Guild>().also { g -> every { g.idLong } returns it }
        }
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        return e
    }

    @Test
    fun `admin user outside home guild replies ephemerally and does not invoke handler`() {
        val h = dispatcher()
        val e = adminEvent(otherGuildId, subcommandGroup = "user", subcommandName = "view")
        h.dispatcher.onSlashCommandInteraction(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured shouldContain "only available in the home guild"
        verify(exactly = 0) { h.adminUser.handle(any()) }
    }

    @Test
    fun `admin user inside home guild invokes handler`() {
        val h = dispatcher()
        val e = adminEvent(homeGuildId, subcommandGroup = "user", subcommandName = "view")
        h.dispatcher.onSlashCommandInteraction(e)
        verify(exactly = 1) { h.adminUser.handle(e) }
        verify(exactly = 0) { e.reply(any<String>()) }
    }

    @Test
    fun `admin run outside home guild replies ephemerally and does not invoke handler`() {
        val h = dispatcher()
        val e = adminEvent(otherGuildId, subcommandGroup = null, subcommandName = "run")
        h.dispatcher.onSlashCommandInteraction(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured shouldContain "only available in the home guild"
        verify(exactly = 0) { h.adminRun.handle(any()) }
    }

    @Test
    fun `admin run inside home guild invokes handler`() {
        val h = dispatcher()
        val e = adminEvent(homeGuildId, subcommandGroup = null, subcommandName = "run")
        h.dispatcher.onSlashCommandInteraction(e)
        verify(exactly = 1) { h.adminRun.handle(e) }
        verify(exactly = 0) { e.reply(any<String>()) }
    }

    @Test
    fun `admin run with null guild (DM) replies ephemerally and does not invoke handler`() {
        val h = dispatcher()
        val e = adminEvent(guildId = null, subcommandGroup = null, subcommandName = "run")
        h.dispatcher.onSlashCommandInteraction(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured shouldContain "only available in the home guild"
        verify(exactly = 0) { h.adminRun.handle(any()) }
    }

    @Test
    fun `admin guild subcommand is not gated by home guild`() {
        val h = dispatcher()
        val e = adminEvent(otherGuildId, subcommandGroup = "guild", subcommandName = "view")
        h.dispatcher.onSlashCommandInteraction(e)
        verify(exactly = 1) { h.adminGuild.handle(e) }
    }

    @Test
    fun `link command is routed to LinkCommand`() {
        val h = dispatcher()
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.name } returns "link"
        h.dispatcher.onSlashCommandInteraction(e)
        verify(exactly = 1) { h.link.handle(e) }
    }

    @Test
    fun `me command is routed to MeCommand`() {
        val h = dispatcher()
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.name } returns "me"
        h.dispatcher.onSlashCommandInteraction(e)
        verify(exactly = 1) { h.me.handle(e) }
    }

    @Test
    fun `relay command is routed to RelayCommand`() {
        val h = dispatcher()
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.name } returns "relay"
        h.dispatcher.onSlashCommandInteraction(e)
        verify(exactly = 1) { h.relay.handle(e) }
    }

    @Test
    fun `unknown admin subcommand replies with Unknown subcommand`() {
        val h = dispatcher()
        val e = adminEvent(homeGuildId, subcommandGroup = null, subcommandName = "bogus")
        h.dispatcher.onSlashCommandInteraction(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured shouldContain "Unknown subcommand"
    }

    @Test
    fun `unknown admin subcommand group replies with Unknown subcommand group`() {
        val h = dispatcher()
        val e = adminEvent(homeGuildId, subcommandGroup = "bogus", subcommandName = "x")
        h.dispatcher.onSlashCommandInteraction(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured shouldContain "Unknown subcommand group"
    }

    @Test
    fun `admin user gate runs before adminUser handler is touched in home guild check`() {
        val adminUser = mockk<AdminUserCommand>(relaxed = true)
        val h = dispatcher(adminUser = adminUser)
        val e = adminEvent(otherGuildId, subcommandGroup = "user", subcommandName = "unlink")
        h.dispatcher.onSlashCommandInteraction(e)
        verify(exactly = 0) { adminUser.handle(any()) }
        e.guild?.idLong shouldBe otherGuildId
    }
}
