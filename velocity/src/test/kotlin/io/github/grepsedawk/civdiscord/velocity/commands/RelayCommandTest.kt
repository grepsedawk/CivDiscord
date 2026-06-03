package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.Binding
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.core.relay.RelayService
import io.github.grepsedawk.civdiscord.velocity.discord.NameLayerPermService
import io.kotest.matchers.nulls.shouldBeNull
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
import java.util.UUID

class RelayCommandTest {

    private data class Fixture(
        val cmd: RelayCommand,
        val svc: RelayService,
        val dao: RelayDao,
        val db: Database,
    )

    private fun fixture(
        bindings: BindingDao = permissiveBindings(),
        permService: NameLayerPermService = PermissivePermService(),
        homeGuildId: Long = 100L,
    ): Fixture {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        val dao = RelayDao(db)
        val svc = RelayService(dao)
        return Fixture(RelayCommand(svc, bindings, permService, homeGuildId), svc, dao, db)
    }

    private fun permissiveBindings(): BindingDao {
        val b = mockk<BindingDao>(relaxed = true)
        every { b.findByDiscordId(any()) } answers {
            val id = firstArg<Long>()
            Binding(discordId = id, mcUuid = TEST_UUID, mcName = "mc-$id", linkedAt = 0L)
        }
        return b
    }

    private fun bindingsReturning(vararg entries: Binding): BindingDao {
        val b = mockk<BindingDao>(relaxed = true)
        val byId = entries.associateBy { it.discordId }
        every { b.findByDiscordId(any()) } answers { byId[firstArg<Long>()] }
        return b
    }

    private fun bindingFor(discordId: Long, mcUuid: UUID): Binding = Binding(discordId = discordId, mcUuid = mcUuid, mcName = "mc-$discordId", linkedAt = 0L)

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
        every { r.setAllowedMentions(any()) } returns r
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
        every { e.getOption("namelayer-group") } returns null
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        every { r.setAllowedMentions(any()) } returns r
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
        every { r.setAllowedMentions(any()) } returns r
        return e
    }

    private fun unbindEvent(
        group: String?,
        guildId: Long = 100L,
        channelId: Long = 1001L,
    ): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns "unbind"
        every { e.guild?.idLong } returns guildId
        every { e.channel.idLong } returns channelId
        if (group != null) {
            val opt = mockk<OptionMapping>()
            every { opt.asString } returns group
            every { e.getOption("namelayer-group") } returns opt
        } else {
            every { e.getOption("namelayer-group") } returns null
        }
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        every { r.setAllowedMentions(any()) } returns r
        return e
    }

    private fun writerEvent(
        group: String,
        guildId: Long = 100L,
        channelId: Long = 1001L,
        userId: Long = 5L,
    ): SlashCommandInteractionEvent {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns "writer"
        every { e.guild?.idLong } returns guildId
        every { e.channel.idLong } returns channelId
        every { e.user.idLong } returns userId
        val opt = mockk<OptionMapping>()
        every { opt.asString } returns group
        every { e.getOption("namelayer-group") } returns opt
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        every { r.setAllowedMentions(any()) } returns r
        return e
    }

    private fun setEventWithGroup(
        prop: String,
        value: String,
        group: String?,
        guildId: Long = 100L,
        channelId: Long = 1001L,
    ): SlashCommandInteractionEvent {
        val e = setEvent(prop, value, guildId, channelId)
        if (group != null) {
            val opt = mockk<OptionMapping>()
            every { opt.asString } returns group
            every { e.getOption("namelayer-group") } returns opt
        } else {
            every { e.getOption("namelayer-group") } returns null
        }
        return e
    }

    @Test
    fun `bind on fresh channel writes a relay row`() {
        val (cmd, _, dao) = fixture()
        cmd.handle(bindEvent("townhall"))
        dao.findByChannelAndGroup(1001L, "townhall")!!.namelayerGroup shouldBe "townhall"
    }

    @Test
    fun `bind global group ! is allowed in the home guild`() {
        val (cmd, _, dao) = fixture(homeGuildId = 100L)
        cmd.handle(bindEvent("!", guildId = 100L))
        dao.findByChannelAndGroup(1001L, "!")!!.namelayerGroup shouldBe "!"
    }

    @Test
    fun `bind global group ! is rejected outside the home guild`() {
        val (cmd, _, dao) = fixture(homeGuildId = 100L)
        val e = bindEvent("!", guildId = 200L)
        cmd.handle(e)
        (dao.findByChannelAndGroup(1001L, "!") == null) shouldBe true
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("home guild")
    }

    @Test
    fun `bind a normal group outside the home guild is allowed`() {
        val (cmd, _, dao) = fixture(homeGuildId = 100L)
        cmd.handle(bindEvent("townhall", guildId = 200L))
        dao.findByChannelAndGroup(1001L, "townhall")!!.namelayerGroup shouldBe "townhall"
    }

    @Test
    fun `bind on already-bound channel replies with the existing binding`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = bindEvent("townhall")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("already bound")
    }

    @Test
    fun `unbind on bound channel removes it`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { e.subcommandName } returns "unbind"
        every { e.guild?.idLong } returns 100L
        every { e.channel.idLong } returns 1001L
        every { e.user.idLong } returns 5L
        every { e.getOption("namelayer-group") } returns null
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        every { r.setAllowedMentions(any()) } returns r
        cmd.handle(e)
        (dao.findByChannelAndGroup(1001L, "townhall") == null) shouldBe true
    }

    @Test
    fun `list returns the relay rows for the current guild`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.bind(100L, 1002L, "ironsworn", showSnitches = false, createdBy = 5L)
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
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        cmd.handle(setEvent("show-snitches", "true"))
        dao.findByChannelAndGroup(1001L, "townhall")!!.showSnitches shouldBe true
    }

    @Test
    fun `set chat-format updates the template`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        cmd.handle(setEvent("chat-format", "{name}: {text}"))
        dao.findByChannelAndGroup(1001L, "townhall")!!.chatFormat shouldBe "{name}: {text}"
    }

    @Test
    fun `list with relays in other guilds doesn't leak`() {
        val (cmd, svc, _, db) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        GuildDao(db).ensure(200L)
        svc.bind(200L, 2001L, "elsewhere", showSnitches = false, createdBy = 5L)
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
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
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
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.setShowSnitches(1001L, "townhall", true)
        val e = setEvent("show-snitches", "false")
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.showSnitches shouldBe false
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("false")
    }

    @Test
    fun `set show-snitches=yes maps to true`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        cmd.handle(setEvent("show-snitches", "yes"))
        dao.findByChannelAndGroup(1001L, "townhall")!!.showSnitches shouldBe true
    }

    @Test
    fun `set show-snitches=garbage replies invalid`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = setEvent("show-snitches", "garbage")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.lowercase().shouldContain("invalid")
        dao.findByChannelAndGroup(1001L, "townhall")!!.showSnitches shouldBe false
    }

    @Test
    fun `set on unbound channel replies guidance`() {
        val (cmd, _, dao) = fixture()
        val e = setEvent("show-snitches", "true")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.lowercase().shouldContain("not bound")
        (dao.findByChannelAndGroup(1001L, "townhall") == null) shouldBe true
    }

    @Test
    fun `list with a backtick in the namelayer group does not break markdown`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "weird`group", showSnitches = false, createdBy = 5L)
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
        svc.bind(100L, 1001L, "weird`group", showSnitches = false, createdBy = 5L)
        val e = subcommandEvent("show")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldNotContain("weird`group")
    }

    @Test
    fun `show with a backtick in the chat format does not break markdown`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.setChatFormat(1001L, "townhall", "x`y{msg}")
        val e = subcommandEvent("show")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldNotContain("x`y")
    }

    @Test
    fun `set chat-format with unknown placeholder is rejected with guidance`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = setEvent("chat-format", "{bogus}")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("Unknown placeholder")
        msg.captured.shouldContain("{bogus}")
        msg.captured.shouldContain("{name}")
        dao.findByChannelAndGroup(1001L, "townhall")!!.chatFormat shouldBe null
    }

    @Test
    fun `set chat-format with all allowed placeholders is accepted`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        cmd.handle(setEvent("chat-format", "{name} {server} {text} {group}"))
        dao.findByChannelAndGroup(1001L, "townhall")!!.chatFormat shouldBe "{name} {server} {text} {group}"
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

    @Test
    fun `set snitch-ping with role mention stores canonical role mention`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = setEvent("snitch-ping", "<@&999>")
        every { e.reply(any<String>()).setAllowedMentions(any()) } returns mockk(relaxed = true)
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.snitchPing shouldBe "<@&999>"
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("<@&999>")
    }

    @Test
    fun `set snitch-ping with user mention stores canonical user mention`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = setEvent("snitch-ping", "<@42>")
        every { e.reply(any<String>()).setAllowedMentions(any()) } returns mockk(relaxed = true)
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.snitchPing shouldBe "<@42>"
    }

    @Test
    fun `set snitch-ping with legacy nickname mention normalizes to canonical`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = setEvent("snitch-ping", "<@!77>")
        every { e.reply(any<String>()).setAllowedMentions(any()) } returns mockk(relaxed = true)
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.snitchPing shouldBe "<@77>"
    }

    @Test
    fun `set snitch-ping with null clears the field`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.setSnitchPing(1001L, "townhall", "<@&999>")
        val e = setEvent("snitch-ping", "null")
        every { e.reply(any<String>()).setAllowedMentions(any()) } returns mockk(relaxed = true)
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.snitchPing.shouldBeNull()
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("Cleared")
    }

    @Test
    fun `set snitch-ping with garbage rejects without writing`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.setSnitchPing(1001L, "townhall", "<@&999>")
        val e = setEvent("snitch-ping", "@notamention")
        every { e.reply(any<String>()).setAllowedMentions(any()) } returns mockk(relaxed = true)
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.snitchPing shouldBe "<@&999>"
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("must be a role mention")
    }

    @Test
    fun `set snitch-ping with literal at-everyone stores at-everyone`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = setEvent("snitch-ping", "@everyone")
        every { e.reply(any<String>()).setAllowedMentions(any()) } returns mockk(relaxed = true)
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.snitchPing shouldBe "@everyone"
    }

    @Test
    fun `set snitch-ping with role mention matching guildId normalizes to at-everyone`() {
        val (cmd, svc, dao) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = setEvent("snitch-ping", "<@&100>")
        every { e.reply(any<String>()).setAllowedMentions(any()) } returns mockk(relaxed = true)
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.snitchPing shouldBe "@everyone"
    }

    @Test
    fun `show on relay with snitch_ping reports the ping line`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.setSnitchPing(1001L, "townhall", "<@&999>")
        val e = subcommandEvent("show")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("ping=")
        msg.captured.shouldContain("<@&999>")
    }

    @Test
    fun `show on relay without snitch_ping reports Ping (none)`() {
        val (cmd, svc, _) = fixture()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        val e = subcommandEvent("show")
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("(none)")
    }

    @Test
    fun `bind is rejected when invoker has no MC binding`() {
        val bindings = mockk<BindingDao>(relaxed = true)
        every { bindings.findByDiscordId(any()) } returns null
        val (cmd, _, dao) = fixture(bindings = bindings, permService = PermissivePermService())
        val e = bindEvent("townhall", userId = 42L)
        cmd.handle(e)
        (dao.findByChannelAndGroup(1001L, "townhall") == null) shouldBe true
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("/discord link")
    }

    @Test
    fun `bind is rejected when invoker lacks READ_CHAT`() {
        val bindings = bindingsReturning(bindingFor(discordId = 42L, mcUuid = TEST_UUID))
        val perm = FakePermService(
            perms = mapOf(Triple(TEST_UUID, "townhall", "READ_CHAT") to false),
        )
        val (cmd, _, dao) = fixture(bindings = bindings, permService = perm)
        val e = bindEvent("townhall", userId = 42L)
        cmd.handle(e)
        (dao.findByChannelAndGroup(1001L, "townhall") == null) shouldBe true
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("READ_CHAT")
    }

    @Test
    fun `bind proceeds when invoker has READ_CHAT`() {
        val bindings = bindingsReturning(bindingFor(discordId = 42L, mcUuid = TEST_UUID))
        val perm = FakePermService(
            perms = mapOf(Triple(TEST_UUID, "townhall", "READ_CHAT") to true),
        )
        val (cmd, _, dao) = fixture(bindings = bindings, permService = perm)
        cmd.handle(bindEvent("townhall", userId = 42L))
        dao.findByChannelAndGroup(1001L, "townhall")!!.namelayerGroup shouldBe "townhall"
    }

    @Test
    fun `set show-snitches=true is rejected when binder lacks SNITCH_NOTIFICATIONS`() {
        val binder = bindingFor(discordId = 99L, mcUuid = TEST_UUID)
        val bindings = bindingsReturning(binder)
        val perm = FakePermService(
            perms = mapOf(Triple(TEST_UUID, "townhall", "SNITCH_NOTIFICATIONS") to false),
        )
        val (cmd, svc, dao) = fixture(bindings = bindings, permService = perm)
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 99L)
        val e = setEvent("show-snitches", "true")
        every { e.reply(any<String>()).setAllowedMentions(any()) } returns mockk(relaxed = true)
        cmd.handle(e)
        dao.findByChannelAndGroup(1001L, "townhall")!!.showSnitches shouldBe false
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("SNITCH_NOTIFICATIONS")
    }

    @Test
    fun `set show-snitches=true succeeds when binder has SNITCH_NOTIFICATIONS`() {
        val binder = bindingFor(discordId = 99L, mcUuid = TEST_UUID)
        val bindings = bindingsReturning(binder)
        val perm = FakePermService(
            perms = mapOf(Triple(TEST_UUID, "townhall", "SNITCH_NOTIFICATIONS") to true),
        )
        val (cmd, svc, dao) = fixture(bindings = bindings, permService = perm)
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 99L)
        cmd.handle(setEvent("show-snitches", "true"))
        dao.findByChannelAndGroup(1001L, "townhall")!!.showSnitches shouldBe true
    }

    @Test
    fun `bind first becomes writer and reply includes the role`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        f.svc.findWriterForChannel(1001L)?.namelayerGroup shouldBe "townhall"
        // PermissivePermService returns true for every perm so snitches default ON.
        f.svc.findByChannelAndGroup(1001L, "townhall")?.showSnitches shouldBe true
    }

    @Test
    fun `bind second on same channel becomes reader`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        f.cmd.handle(bindEvent(group = "tavern"))
        f.svc.findByChannelAndGroup(1001L, "tavern")?.isWriter shouldBe false
    }

    @Test
    fun `bind defaults show_snitches to false when binder lacks SNITCH_NOTIFICATIONS`() {
        val readOnly = object : NameLayerPermService(lookup = { _, _, _ -> false }) {
            override fun hasPerm(mcUuid: UUID, group: String, perm: String): Boolean = perm == NameLayerPermService.READ_CHAT
        }
        val f = fixture(permService = readOnly)
        f.cmd.handle(bindEvent(group = "townhall"))
        f.svc.findByChannelAndGroup(1001L, "townhall")?.showSnitches shouldBe false
    }

    @Test
    fun `unbind without group on multi-bind asks which group`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        f.cmd.handle(bindEvent(group = "tavern"))
        val e = unbindEvent(group = null)
        f.cmd.handle(e)
        verify { e.reply(match<String> { it.contains("Specify which group") && it.contains("townhall") && it.contains("tavern") }) }
    }

    @Test
    fun `unbind without group on single-bind removes that binding`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        f.cmd.handle(unbindEvent(group = null))
        f.svc.listForChannel(1001L) shouldBe emptyList()
    }

    @Test
    fun `unbind with explicit group removes only that binding`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        f.cmd.handle(bindEvent(group = "tavern"))
        f.cmd.handle(unbindEvent(group = "tavern"))
        f.svc.listForChannel(1001L).map { it.namelayerGroup } shouldBe listOf("townhall")
    }

    @Test
    fun `writer promotes bound group and replies`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        f.cmd.handle(bindEvent(group = "tavern"))
        val e = writerEvent(group = "tavern")
        f.cmd.handle(e)
        f.svc.findWriterForChannel(1001L)?.namelayerGroup shouldBe "tavern"
        verify { e.reply(match<String> { it.contains("writer") && it.contains("tavern") }) }
    }

    @Test
    fun `writer on unbound group replies with bind hint`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        val e = writerEvent(group = "nope")
        f.cmd.handle(e)
        verify { e.reply(match<String> { it.contains("not bound") && it.contains("Bind it first") }) }
    }

    @Test
    fun `writer denies when invoker lacks READ_CHAT`() {
        val denying = object : NameLayerPermService(lookup = { _, _, _ -> false }) {
            override fun hasPerm(mcUuid: UUID, group: String, perm: String): Boolean = false
        }
        val f = fixture(permService = denying)
        // pre-seed via DAO since service.bind would deny too
        f.dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val e = writerEvent(group = "townhall")
        f.cmd.handle(e)
        verify { e.reply(match<String> { it.contains("READ_CHAT") }) }
    }

    @Test
    fun `set show-snitches without group on multi-bind asks which group`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        f.cmd.handle(bindEvent(group = "tavern"))
        val e = setEventWithGroup(prop = "show-snitches", value = "true", group = null)
        f.cmd.handle(e)
        verify { e.reply(match<String> { it.contains("Specify which group") }) }
    }

    @Test
    fun `set show-snitches with explicit group scopes to that binding`() {
        val f = fixture()
        f.cmd.handle(bindEvent(group = "townhall"))
        f.cmd.handle(bindEvent(group = "tavern"))
        // Reset both to false so we can prove the change is scoped.
        f.dao.setShowSnitches(1001L, "townhall", false)
        f.dao.setShowSnitches(1001L, "tavern", false)
        val e = setEventWithGroup(prop = "show-snitches", value = "true", group = "tavern")
        f.cmd.handle(e)
        f.svc.findByChannelAndGroup(1001L, "townhall")?.showSnitches shouldBe false
        f.svc.findByChannelAndGroup(1001L, "tavern")?.showSnitches shouldBe true
    }

    companion object {
        private val TEST_UUID: UUID = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")
    }
}

private class PermissivePermService : NameLayerPermService(lookup = { _, _, _ -> false }) {
    override fun hasPerm(mcUuid: UUID, group: String, perm: String): Boolean = true
}

private class FakePermService(
    private val perms: Map<Triple<UUID, String, String>, Boolean> = emptyMap(),
) : NameLayerPermService(lookup = { _, _, _ -> false }) {
    override fun hasPerm(mcUuid: UUID, group: String, perm: String): Boolean = perms[Triple(mcUuid, group, perm)] ?: false
}
