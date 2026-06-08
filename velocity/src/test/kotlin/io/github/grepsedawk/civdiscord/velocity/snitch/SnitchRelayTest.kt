package io.github.grepsedawk.civdiscord.velocity.snitch

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.Binding
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.core.relay.SnitchPing
import io.github.grepsedawk.civdiscord.velocity.discord.NameLayerPermService
import io.github.grepsedawk.civdiscord.velocity.discord.PermCheck
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class SnitchRelayTest {

    private class CapturingLogger : Logger by LoggerFactory.getLogger("capture") {
        val warns = mutableListOf<String>()
        val infos = mutableListOf<String>()

        override fun warn(msg: String) {
            warns += msg
        }

        override fun info(msg: String) {
            infos += msg
        }

        override fun info(msg: String, arg: Any?) {
            infos += format(msg, arrayOf(arg))
        }

        override fun info(msg: String, arg1: Any?, arg2: Any?) {
            infos += format(msg, arrayOf(arg1, arg2))
        }

        override fun info(msg: String, vararg args: Any?) {
            infos += format(msg, args)
        }

        private fun format(msg: String, args: Array<out Any?>): String {
            var result = msg
            for (a in args) {
                val idx = result.indexOf("{}")
                if (idx < 0) break
                result = result.substring(0, idx) + a.toString() + result.substring(idx + 2)
            }
            return result
        }
    }

    private data class Sent(val channelId: Long, val body: String, val ping: SnitchPing?)

    private val testUuid: UUID = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")

    private fun permissiveBindings(): BindingDao {
        val b = mockk<BindingDao>(relaxed = true)
        every { b.findByDiscordId(any()) } answers {
            Binding(discordId = firstArg(), mcUuid = testUuid, mcName = "mc", linkedAt = 0L)
        }
        return b
    }

    private class PermissivePermService : NameLayerPermService(lookup = { _, _, _ -> PermCheck.DENIED }) {
        override fun hasPerm(mcUuid: UUID, group: String, perm: String) = perm != NameLayerPermService.SNITCH_IMMUNE
    }

    private class FakePermService(
        private val perms: Map<Triple<UUID, String, String>, Boolean> = emptyMap(),
    ) : NameLayerPermService(lookup = { _, _, _ -> PermCheck.DENIED }) {
        override fun hasPerm(mcUuid: UUID, group: String, perm: String): Boolean = perms[Triple(mcUuid, group, perm)] ?: false
    }

    private fun fixture(
        logger: Logger = LoggerFactory.getLogger("test"),
        bindings: BindingDao = permissiveBindings(),
        permService: NameLayerPermService = PermissivePermService(),
    ): Triple<SnitchRelay, RelayDao, MutableList<Sent>> {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        val relays = RelayDao(db)
        val sent = mutableListOf<Sent>()
        val relay = SnitchRelay(
            relays = relays,
            bindings = bindings,
            permService = permService,
            sendToDiscord = { ch, txt, ping -> sent.add(Sent(ch, txt, ping)) },
            logger = logger,
        )
        return Triple(relay, relays, sent)
    }

    private fun hit(group: String = "townhall", kind: String = "ENTER") = Payload.SnitchHit(
        server = "citadel",
        snitchOwnerUuid = "00000000-0000-0000-0000-000000000001",
        intruderUuid = "00000000-0000-0000-0000-000000000002",
        x = 10,
        y = 64,
        z = -3,
        snitchName = "TownNorth",
        namelayerGroup = group,
        kind = kind,
    )

    @Test
    fun `routes to every relay with show_snitches enabled for the group`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L) shouldBe RelayDao.BindOutcome.Inserted
        dao.setShowSnitches(1001L, "townhall", true) shouldBe 1
        dao.bind(200L, 2001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L) shouldBe RelayDao.BindOutcome.Inserted
        dao.setShowSnitches(2001L, "townhall", true) shouldBe 1

        relay.dispatch(hit())

        sent.map { it.channelId }.toSet() shouldBe setOf(1001L, 2001L)
        sent.all { it.body.contains("SNITCH") } shouldBe true
        sent.all { it.body.contains("hit") } shouldBe true
        sent.all { it.ping == null } shouldBe true
    }

    @Test
    fun `skips relays where show_snitches is false`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.bind(200L, 2001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(2001L, "townhall", true)

        relay.dispatch(hit())

        sent.map { it.channelId } shouldBe listOf(2001L)
    }

    @Test
    fun `skips relays for a different group`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "other", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "other", true)

        relay.dispatch(hit("townhall"))

        sent shouldBe emptyList()
    }

    @Test
    fun `LOGIN renders as login and LOGOUT as logout`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)

        relay.dispatch(hit(kind = "LOGIN"))
        relay.dispatch(hit(kind = "LOGOUT"))

        sent.size shouldBe 2
        sent[0].body.contains("[login]") shouldBe true
        sent[1].body.contains("[logout]") shouldBe true
    }

    @Test
    fun `escapes markdown in snitch name`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)

        relay.dispatch(hit().copy(snitchName = "evil`name"))

        sent[0].body.contains("evil`name") shouldBe false
    }

    @Test
    fun `warns and drops when no relays are bound to the group`() {
        val log = CapturingLogger()
        val (relay, _, sent) = fixture(logger = log)

        relay.dispatch(hit("ghosttown"))

        sent shouldBe emptyList()
        log.warns.size shouldBe 1
        log.warns.first().contains("no relays bound to NameLayer group") shouldBe true
        log.warns.first().contains("ghosttown") shouldBe true
    }

    @Test
    fun `warns and drops when all matching relays have show_snitches disabled`() {
        val log = CapturingLogger()
        val (relay, dao, sent) = fixture(logger = log)
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.bind(200L, 2001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)

        relay.dispatch(hit())

        sent shouldBe emptyList()
        log.warns.size shouldBe 1
        log.warns.first().contains("none have show_snitches=true") shouldBe true
        log.warns.first().contains("townhall") shouldBe true
    }

    @Test
    fun `fans out and logs info on the happy path`() {
        val log = CapturingLogger()
        val (relay, dao, sent) = fixture(logger = log)
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)

        relay.dispatch(hit())

        sent.size shouldBe 1
        log.infos.any { it.contains("fanned out") } shouldBe true
    }

    @Test
    fun `renders intruder name when present and falls back to UUID when absent`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)

        relay.dispatch(hit().copy(intruderName = "grepsedawk"))
        relay.dispatch(hit())
        relay.dispatch(hit().copy(intruderName = ""))

        sent.size shouldBe 3
        sent[0].body.contains("grepsedawk") shouldBe true
        sent[0].body.contains("00000000-0000-0000-0000-000000000002") shouldBe false
        sent[1].body.contains("00000000-0000-0000-0000-000000000002") shouldBe true
        sent[2].body.contains("00000000-0000-0000-0000-000000000002") shouldBe true
    }

    @Test
    fun `repeated drops on the same group emit a single warn within the rate-limit window`() {
        val log = CapturingLogger()
        val (relay, _, sent) = fixture(logger = log)

        repeat(50) { relay.dispatch(hit("ghosttown")) }

        sent shouldBe emptyList()
        log.warns.size shouldBe 1
    }

    @Test
    fun `prepends role mention and passes typed ping when snitch_ping is a role`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)
        dao.setSnitchPing(1001L, "townhall", "<@&999>")

        relay.dispatch(hit())

        sent.size shouldBe 1
        sent[0].channelId shouldBe 1001L
        sent[0].body.startsWith("<@&999> ") shouldBe true
        sent[0].ping shouldBe SnitchPing.Role(999L)
    }

    @Test
    fun `prepends user mention and passes typed ping when snitch_ping is a user`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)
        dao.setSnitchPing(1001L, "townhall", "<@42>")

        relay.dispatch(hit())

        sent.size shouldBe 1
        sent[0].body.startsWith("<@42> ") shouldBe true
        sent[0].ping shouldBe SnitchPing.User(42L)
    }

    @Test
    fun `legacy nickname mention stored value normalizes to canonical user ping at dispatch`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)
        dao.setSnitchPing(1001L, "townhall", "<@!42>")

        relay.dispatch(hit())

        sent.size shouldBe 1
        sent[0].body.startsWith("<@42> ") shouldBe true
        sent[0].ping shouldBe SnitchPing.User(42L)
    }

    @Test
    fun `per-relay independence — ping only the relay that opts in`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)
        dao.setSnitchPing(1001L, "townhall", "<@&999>")
        dao.bind(200L, 2001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(2001L, "townhall", true)
        // no snitch_ping on 2001

        relay.dispatch(hit())

        sent.size shouldBe 2
        val pinged = sent.first { it.channelId == 1001L }
        val unpinged = sent.first { it.channelId == 2001L }
        pinged.body.startsWith("<@&999> ") shouldBe true
        pinged.ping shouldBe SnitchPing.Role(999L)
        unpinged.body.startsWith("<@&") shouldBe false
        unpinged.body.startsWith("<@") shouldBe false
        unpinged.ping shouldBe null
    }

    @Test
    fun `role mention whose id matches the guild id substitutes to Everyone`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)
        dao.setSnitchPing(1001L, "townhall", "<@&100>")

        relay.dispatch(hit())

        sent.size shouldBe 1
        sent[0].body.startsWith("@everyone ") shouldBe true
        sent[0].ping shouldBe SnitchPing.Everyone
    }

    @Test
    fun `literal at-everyone stored value dispatches as Everyone`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)
        dao.setSnitchPing(1001L, "townhall", "@everyone")

        relay.dispatch(hit())

        sent.size shouldBe 1
        sent[0].body.startsWith("@everyone ") shouldBe true
        sent[0].ping shouldBe SnitchPing.Everyone
    }

    @Test
    fun `role id matching a different guild is not substituted to Everyone`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)
        // role id 200 == guild 200's id, but this relay belongs to guild 100
        dao.setSnitchPing(1001L, "townhall", "<@&200>")

        relay.dispatch(hit())

        sent.size shouldBe 1
        sent[0].body.startsWith("<@&200> ") shouldBe true
        sent[0].ping shouldBe SnitchPing.Role(200L)
    }

    @Test
    fun `malformed stored snitch_ping is treated as no ping`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)
        dao.setSnitchPing(1001L, "townhall", "@notamention")

        relay.dispatch(hit())

        sent.size shouldBe 1
        sent[0].body.startsWith("@notamention") shouldBe false
        sent[0].body.startsWith("<@") shouldBe false
        sent[0].ping shouldBe null
    }

    @Test
    fun `silently drops the send when binder lacks SNITCH_NOTIFICATIONS and does not unbind`() {
        val (relay, dao, sent) = fixture(permService = FakePermService())
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)

        relay.dispatch(hit())

        sent shouldBe emptyList()
        (dao.findByChannelAndGroup(1001L, "townhall") != null) shouldBe true
    }

    @Test
    fun `silently drops the send when binder binding has vanished and does not unbind`() {
        val bindings = mockk<BindingDao>(relaxed = true)
        every { bindings.findByDiscordId(any()) } returns null
        val (relay, dao, sent) = fixture(bindings = bindings)
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 1L)
        dao.setShowSnitches(1001L, "townhall", true)

        relay.dispatch(hit())

        sent shouldBe emptyList()
        (dao.findByChannelAndGroup(1001L, "townhall") != null) shouldBe true
    }

    @Test
    fun `fans out a hit to writer-channel and reader-channel both bound to same group with snitches on`() {
        val (sr, relays, sent) = fixture()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = true, createdBy = 5L)
        relays.bind(100L, 2001L, "townhall", isWriter = false, showSnitches = true, createdBy = 5L)
        sr.dispatch(hit(group = "townhall"))
        sent.map { it.channelId }.sorted() shouldBe listOf(1001L, 2001L)
    }

    @Test
    fun `reader with showSnitches off is skipped`() {
        val (sr, relays, sent) = fixture()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = true, createdBy = 5L)
        relays.bind(100L, 2001L, "townhall", isWriter = false, showSnitches = false, createdBy = 5L)
        sr.dispatch(hit(group = "townhall"))
        sent.map { it.channelId } shouldBe listOf(1001L)
    }

    @Test
    fun `drops the hit entirely when the intruder has SNITCH_IMMUNE on the group`() {
        val intruderUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val perms = mapOf(
            Triple(intruderUuid, "townhall", "SNITCH_IMMUNE") to true,
            Triple(testUuid, "townhall", "SNITCH_NOTIFICATIONS") to true,
        )
        val (relay, dao, sent) = fixture(permService = FakePermService(perms))
        dao.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = true, createdBy = 1L)

        relay.dispatch(hit())

        sent shouldBe emptyList()
    }
}
