package io.github.grepsedawk.civdiscord.velocity.snitch

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

    private fun fixture(
        logger: Logger = LoggerFactory.getLogger("test"),
    ): Triple<SnitchRelay, RelayDao, MutableList<Pair<Long, String>>> {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        val relays = RelayDao(db)
        val sent = mutableListOf<Pair<Long, String>>()
        val relay = SnitchRelay(
            relays = relays,
            sendToDiscord = { ch, txt -> sent.add(ch to txt) },
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
        val a = (dao.bind(100L, 1001L, "townhall", 1L) as RelayDao.BindOutcome.Inserted)
        dao.setShowSnitches(1001L, true) shouldBe 1
        val b = (dao.bind(200L, 2001L, "townhall", 1L) as RelayDao.BindOutcome.Inserted)
        dao.setShowSnitches(2001L, true) shouldBe 1
        a.id shouldBe a.id
        b.id shouldBe b.id

        relay.dispatch(hit())

        sent.map { it.first }.toSet() shouldBe setOf(1001L, 2001L)
        sent.all { it.second.contains("SNITCH") } shouldBe true
        sent.all { it.second.contains("hit") } shouldBe true
    }

    @Test
    fun `skips relays where show_snitches is false`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", 1L)
        dao.bind(200L, 2001L, "townhall", 1L)
        dao.setShowSnitches(2001L, true)

        relay.dispatch(hit())

        sent.map { it.first } shouldBe listOf(2001L)
    }

    @Test
    fun `skips relays for a different group`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "other", 1L)
        dao.setShowSnitches(1001L, true)

        relay.dispatch(hit("townhall"))

        sent shouldBe emptyList()
    }

    @Test
    fun `LOGIN renders as login and LOGOUT as logout`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", 1L)
        dao.setShowSnitches(1001L, true)

        relay.dispatch(hit(kind = "LOGIN"))
        relay.dispatch(hit(kind = "LOGOUT"))

        sent.size shouldBe 2
        sent[0].second.contains("[login]") shouldBe true
        sent[1].second.contains("[logout]") shouldBe true
    }

    @Test
    fun `escapes markdown in snitch name`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", 1L)
        dao.setShowSnitches(1001L, true)

        relay.dispatch(hit().copy(snitchName = "evil`name"))

        sent[0].second.contains("evil`name") shouldBe false
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
        dao.bind(100L, 1001L, "townhall", 1L)
        dao.bind(200L, 2001L, "townhall", 1L)

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
        dao.bind(100L, 1001L, "townhall", 1L)
        dao.setShowSnitches(1001L, true)

        relay.dispatch(hit())

        sent.size shouldBe 1
        log.infos.any { it.contains("fanned out") } shouldBe true
    }

    @Test
    fun `renders intruder name when present and falls back to UUID when absent`() {
        val (relay, dao, sent) = fixture()
        dao.bind(100L, 1001L, "townhall", 1L)
        dao.setShowSnitches(1001L, true)

        relay.dispatch(hit().copy(intruderName = "grepsedawk"))
        relay.dispatch(hit())
        relay.dispatch(hit().copy(intruderName = ""))

        sent.size shouldBe 3
        sent[0].second.contains("grepsedawk") shouldBe true
        sent[0].second.contains("00000000-0000-0000-0000-000000000002") shouldBe false
        sent[1].second.contains("00000000-0000-0000-0000-000000000002") shouldBe true
        sent[2].second.contains("00000000-0000-0000-0000-000000000002") shouldBe true
    }

    @Test
    fun `repeated drops on the same group emit a single warn within the rate-limit window`() {
        val log = CapturingLogger()
        val (relay, _, sent) = fixture(logger = log)

        repeat(50) { relay.dispatch(hit("ghosttown")) }

        sent shouldBe emptyList()
        log.warns.size shouldBe 1
    }
}
