package io.github.grepsedawk.civdiscord.velocity.snitch

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SnitchRelayTest {

    private fun fixture(): Triple<SnitchRelay, RelayDao, MutableList<Pair<Long, String>>> {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        val relays = RelayDao(db)
        val sent = mutableListOf<Pair<Long, String>>()
        val relay = SnitchRelay(relays = relays, sendToDiscord = { ch, txt -> sent.add(ch to txt) })
        return Triple(relay, relays, sent)
    }

    private fun hit(group: String = "townhall", kind: String = "ENTER") =
        Payload.SnitchHit(
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
}
