package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.core.relay.RelayService
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
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Test

class RelayCommandTest {

    private data class Fixture(
        val cmd: RelayCommand,
        val svc: RelayService,
        val dao: RelayDao,
        val db: Database,
    )

    private fun fixture(): Fixture {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val dao = RelayDao(db)
        val svc = RelayService(dao)
        return Fixture(RelayCommand(svc), svc, dao, db)
    }

    private fun bindEvent(
        group: String,
        guildId: Long = 100L,
        channelId: Long = 1001L,
        userId: Long = 5L,
    ): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns "bind"
        val groupOpt = mockk<OptionMapping>()
        every { groupOpt.asString } returns group
        every { e.getOption("namelayer-group") } returns groupOpt
        every { e.guild?.idLong } returns guildId
        every { e.channel.idLong } returns channelId
        every { e.user.idLong } returns userId
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        return e
    }

    private fun setEvent(
        prop: String,
        value: String,
        guildId: Long = 100L,
        channelId: Long = 1001L,
    ): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns "set"
        every { e.guild?.idLong } returns guildId
        every { e.channel.idLong } returns channelId
        val propOpt = mockk<OptionMapping>()
        every { propOpt.asString } returns prop
        val valOpt = mockk<OptionMapping>()
        every { valOpt.asString } returns value
        every { e.getOption("property") } returns propOpt
        every { e.getOption("value") } returns valOpt
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        return e
    }

    private fun subcommandEvent(
        sub: String,
        guildId: Long = 100L,
        channelId: Long = 1001L,
    ): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns sub
        every { e.guild?.idLong } returns guildId
        every { e.channel.idLong } returns channelId
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        return e
    }

    @Test
    fun `bind on fresh channel writes a relay row`() {
        val (cmd, _, dao) = fixture()
        cmd.handle(bindEvent("townhall"))
        dao.findByChannel(1001L)!!.namelayerGroup shouldBe "townhall"
    }

    @Test
    fun `bind on already-bound channel replies with the existing binding`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        val e = bindEvent("townhall")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("already bound")
    }

    @Test
    fun `unbind on bound channel removes it`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns "unbind"
        every { e.guild?.idLong } returns 100L
        every { e.channel.idLong } returns 1001L
        every { e.user.idLong } returns 5L
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        cmd.handle(e)
        (dao.findByChannel(1001L) == null) shouldBe true
    }

    @Test
    fun `list returns the relay rows for the current guild`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        svc.bind(100L, 1002L, "ironsworn", 5L)
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns "list"
        every { e.guild?.idLong } returns 100L
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("townhall")
        msg.captured.shouldContain("ironsworn")
    }

    @Test
    fun `set show-snitches=true updates the row`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        cmd.handle(setEvent("show-snitches", "true"))
        dao.findByChannel(1001L)!!.showSnitches shouldBe true
    }

    @Test
    fun `set chat-format updates the template`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        cmd.handle(setEvent("chat-format", "{name}: {text}"))
        dao.findByChannel(1001L)!!.chatFormat shouldBe "{name}: {text}"
    }

    @Test
    fun `list with relays in other guilds doesn't leak`() {
        val (cmd, svc, _, db) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        GuildDao(db).ensure(200L)
        svc.bind(200L, 2001L, "elsewhere", 5L)
        val e = subcommandEvent("list", guildId = 100L)
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("townhall")
        (msg.captured.contains("elsewhere")) shouldBe false
    }

    @Test
    fun `show on bound channel reports the binding`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        val e = subcommandEvent("show")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("townhall")
        msg.captured.shouldContain("<#1001>")
    }

    @Test
    fun `show on unbound channel reports not bound`() {
        val (cmd, _, _) = fixture()
        val e = subcommandEvent("show")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.lowercase().shouldContain("not bound")
    }

    @Test
    fun `set show-snitches=false updates the row`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        svc.setShowSnitches(1001L, true)
        val e = setEvent("show-snitches", "false")
        cmd.handle(e)
        dao.findByChannel(1001L)!!.showSnitches shouldBe false
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("false")
    }

    @Test
    fun `set show-snitches=yes maps to true`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        cmd.handle(setEvent("show-snitches", "yes"))
        dao.findByChannel(1001L)!!.showSnitches shouldBe true
    }

    @Test
    fun `set show-snitches=garbage replies invalid`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        val e = setEvent("show-snitches", "garbage")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.lowercase().shouldContain("invalid")
        dao.findByChannel(1001L)!!.showSnitches shouldBe false
    }

    @Test
    fun `set on unbound channel replies guidance`() {
        val (cmd, _, dao) = fixture()
        val e = setEvent("show-snitches", "true")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.lowercase().shouldContain("not bound")
        (dao.findByChannel(1001L) == null) shouldBe true
    }

    @Test
    fun `list with a backtick in the namelayer group does not break markdown`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "weird`group", 5L)
        val e = subcommandEvent("list")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldNotContain("weird`group")
        msg.captured.count { it == '`' } shouldBe 2
    }

    @Test
    fun `show with a backtick in the namelayer group does not break markdown`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "weird`group", 5L)
        val e = subcommandEvent("show")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldNotContain("weird`group")
    }

    @Test
    fun `show with a backtick in the chat format does not break markdown`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        svc.setChatFormat(1001L, "x`y{msg}")
        val e = subcommandEvent("show")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldNotContain("x`y")
    }

    @Test
    fun `set chat-format with unknown placeholder is rejected with guidance`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        val e = setEvent("chat-format", "{bogus}")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("Unknown placeholder")
        msg.captured.shouldContain("{bogus}")
        msg.captured.shouldContain("{name}")
        dao.findByChannel(1001L)!!.chatFormat shouldBe null
    }

    @Test
    fun `set chat-format with all allowed placeholders is accepted`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", 5L)
        cmd.handle(setEvent("chat-format", "{name} {server} {text} {group}"))
        dao.findByChannel(1001L)!!.chatFormat shouldBe "{name} {server} {text} {group}"
    }

    @Test
    fun `bind with a backtick in the namelayer group does not break markdown`() {
        val (cmd, _, _) = fixture()
        cmd.handle(bindEvent("weird`group"))
        val e = bindEvent("weird`group")
        cmd.handle(e)
        val msg = slot<String>()
        verify(atLeast = 1) { e.reply(capture(msg)) }
        msg.captured.shouldNotContain("weird`group")
    }
}
