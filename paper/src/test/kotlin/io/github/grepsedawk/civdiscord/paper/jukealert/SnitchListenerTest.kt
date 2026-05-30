package io.github.grepsedawk.civdiscord.paper.jukealert

import be.seeseemelk.mockbukkit.MockBukkit
import com.untamedears.jukealert.events.PlayerHitSnitchEvent
import com.untamedears.jukealert.events.PlayerLoginSnitchEvent
import com.untamedears.jukealert.events.PlayerLogoutSnitchEvent
import com.untamedears.jukealert.model.Snitch
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.helpers.MessageFormatter
import vg.civcraft.mc.namelayer.group.Group
import java.util.UUID

class SnitchListenerTest {

    private val intruder: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val owner: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach fun setup() {
        MockBukkit.mock()
    }

    @AfterEach fun teardown() {
        MockBukkit.unmock()
    }

    private class CapturingLogger : Logger by LoggerFactory.getLogger("capture") {
        val warns = mutableListOf<String>()
        val infos = mutableListOf<String>()
        override fun warn(msg: String) {
            warns += msg
        }
        override fun warn(msg: String, arg: Any?) {
            warns += MessageFormatter.format(msg, arg).message
        }
        override fun warn(msg: String, arg1: Any?, arg2: Any?) {
            warns += MessageFormatter.format(msg, arg1, arg2).message
        }
        override fun warn(msg: String, vararg args: Any?) {
            warns += MessageFormatter.arrayFormat(msg, args).message
        }
        override fun info(msg: String) {
            infos += msg
        }
        override fun info(msg: String, arg: Any?) {
            infos += MessageFormatter.format(msg, arg).message
        }
        override fun info(msg: String, arg1: Any?, arg2: Any?) {
            infos += MessageFormatter.format(msg, arg1, arg2).message
        }
        override fun info(msg: String, vararg args: Any?) {
            infos += MessageFormatter.arrayFormat(msg, args).message
        }
    }

    private fun summary() = SnitchListener.SnitchSummary(
        name = "TownNorth",
        ownerUuid = owner.toString(),
        namelayerGroup = "townhall",
        x = 10,
        y = 64,
        z = -3,
    )

    private fun mockPlayer(): Player {
        val p = mockk<Player>()
        every { p.uniqueId } returns intruder
        every { p.name } returns "Alice"
        return p
    }

    private fun mockGroup(name: String?): Group {
        val g = mockk<Group>()
        every { g.name } returns name
        every { g.owner } returns owner
        return g
    }

    private fun mockSnitch(groupName: String? = "townhall", snitchName: String = "TownNorth"): Snitch {
        val s = mockk<Snitch>()
        every { s.id } returns 42
        every { s.name } returns snitchName
        every { s.group } returns mockGroup(groupName)
        every { s.placer } returns owner
        val loc = mockk<Location>()
        every { loc.blockX } returns 10
        every { loc.blockY } returns 64
        every { loc.blockZ } returns -3
        every { s.location } returns loc
        return s
    }

    @Test
    fun `report produces a SnitchHit payload`() {
        val sent = mutableListOf<Payload>()
        val listener = SnitchListener(serverName = "citadel", send = sent::add)
        listener.report(
            intruderUuid = intruder.toString(),
            ownerUuid = owner.toString(),
            x = 10,
            y = 64,
            z = -3,
            snitchName = "TownNorth",
            namelayerGroup = "townhall",
            kind = SnitchListener.Kind.ENTER,
        )
        (sent[0] as Payload.SnitchHit).kind shouldBe "ENTER"
        (sent[0] as Payload.SnitchHit).server shouldBe "citadel"
    }

    @Test
    fun `dispatch ENTER emits SnitchHit with player uuid and summary fields`() {
        val sent = mutableListOf<Payload>()
        val listener = SnitchListener(serverName = "citadel", send = sent::add)

        listener.dispatch(mockPlayer(), summary(), SnitchListener.Kind.ENTER)

        val hit = sent.single() as Payload.SnitchHit
        hit.kind shouldBe "ENTER"
        hit.server shouldBe "citadel"
        hit.intruderUuid shouldBe intruder.toString()
        hit.snitchOwnerUuid shouldBe owner.toString()
        hit.snitchName shouldBe "TownNorth"
        hit.namelayerGroup shouldBe "townhall"
        hit.x shouldBe 10
        hit.y shouldBe 64
        hit.z shouldBe -3
    }

    @Test
    fun `dispatch LOGIN tags kind LOGIN`() {
        val sent = mutableListOf<Payload>()
        SnitchListener(serverName = "s", send = sent::add)
            .dispatch(mockPlayer(), summary(), SnitchListener.Kind.LOGIN)
        (sent.single() as Payload.SnitchHit).kind shouldBe "LOGIN"
    }

    @Test
    fun `dispatch LOGOUT tags kind LOGOUT`() {
        val sent = mutableListOf<Payload>()
        SnitchListener(serverName = "s", send = sent::add)
            .dispatch(mockPlayer(), summary(), SnitchListener.Kind.LOGOUT)
        (sent.single() as Payload.SnitchHit).kind shouldBe "LOGOUT"
    }

    @Test
    fun `onHit is annotated EventHandler so Bukkit dispatches PlayerHitSnitchEvent`() {
        val m = SnitchListener::class.java.getDeclaredMethod(
            "onHit",
            PlayerHitSnitchEvent::class.java,
        )
        (m.getAnnotation(EventHandler::class.java) != null) shouldBe true
    }

    @Test
    fun `onLogin is annotated EventHandler so Bukkit dispatches PlayerLoginSnitchEvent`() {
        val m = SnitchListener::class.java.getDeclaredMethod(
            "onLogin",
            PlayerLoginSnitchEvent::class.java,
        )
        (m.getAnnotation(EventHandler::class.java) != null) shouldBe true
    }

    @Test
    fun `onLogout is annotated EventHandler so Bukkit dispatches PlayerLogoutSnitchEvent`() {
        val m = SnitchListener::class.java.getDeclaredMethod(
            "onLogout",
            PlayerLogoutSnitchEvent::class.java,
        )
        (m.getAnnotation(EventHandler::class.java) != null) shouldBe true
    }

    @Test
    fun `handle with null player logs warn and emits nothing`() {
        val sent = mutableListOf<Payload>()
        val log = CapturingLogger()
        val listener = SnitchListener(serverName = "s", send = sent::add, logger = log)

        listener.handle(mockSnitch(), null, SnitchListener.Kind.ENTER)

        sent shouldBe emptyList()
        log.warns shouldHaveSize 1
        log.warns.first() shouldContain "event.player was null"
    }

    @Test
    fun `handle with null snitch logs warn and emits nothing`() {
        val sent = mutableListOf<Payload>()
        val log = CapturingLogger()
        val listener = SnitchListener(serverName = "s", send = sent::add, logger = log)

        listener.handle(null, mockPlayer(), SnitchListener.Kind.ENTER)

        sent shouldBe emptyList()
        log.warns shouldHaveSize 1
        log.warns.first() shouldContain "event.snitch was null"
    }

    @Test
    fun `handle with blank namelayer group logs warn and emits nothing`() {
        val sent = mutableListOf<Payload>()
        val log = CapturingLogger()
        val listener = SnitchListener(serverName = "s", send = sent::add, logger = log)

        val snitch = mockk<Snitch>()
        every { snitch.id } returns 7
        every { snitch.name } returns "OrphanSnitch"
        every { snitch.group } returns null
        every { snitch.placer } returns owner
        val loc = mockk<Location>()
        every { loc.blockX } returns 1
        every { loc.blockY } returns 2
        every { loc.blockZ } returns 3
        every { snitch.location } returns loc

        listener.handle(snitch, mockPlayer(), SnitchListener.Kind.ENTER)

        sent shouldBe emptyList()
        log.warns shouldHaveSize 1
        log.warns.first() shouldContain "has no NameLayer group"
    }

    @Test
    fun `handle happy path emits one SnitchHit and logs info`() {
        val sent = mutableListOf<Payload>()
        val log = CapturingLogger()
        val listener = SnitchListener(serverName = "citadel", send = sent::add, logger = log)

        listener.handle(mockSnitch(), mockPlayer(), SnitchListener.Kind.ENTER)

        sent shouldHaveSize 1
        val hit = sent.single() as Payload.SnitchHit
        hit.namelayerGroup shouldBe "townhall"
        hit.kind shouldBe "ENTER"
        log.infos.size shouldBe 1
        log.infos.first() shouldContain "SnitchHit dispatch"
        log.warns shouldBe emptyList()
    }

    @Test
    fun `onHit catches exceptions from summarize so Bukkit does not swallow them`() {
        val sent = mutableListOf<Payload>()
        val log = CapturingLogger()
        val listener = SnitchListener(serverName = "s", send = sent::add, logger = log)

        val explodingSnitch = mockk<Snitch>()
        every { explodingSnitch.id } returns 99
        every { explodingSnitch.group } returns mockGroup("townhall")
        every { explodingSnitch.name } throws RuntimeException("kaboom")
        every { explodingSnitch.placer } returns owner
        every { explodingSnitch.location } returns mockk(relaxed = true)

        val event = mockk<PlayerHitSnitchEvent>()
        every { event.snitch } returns explodingSnitch
        every { event.player } returns mockPlayer()

        listener.onHit(event)

        sent shouldBe emptyList()
        log.warns shouldHaveSize 1
        log.warns.first() shouldContain "threw"
        log.warns.first() shouldContain "Bukkit would have swallowed this"
    }

    @Test
    fun `onLogin defers the dispatch to scheduleLogin so the carrier pipeline can settle`() {
        val sent = mutableListOf<Payload>()
        val deferred = mutableListOf<Runnable>()
        val listener = SnitchListener(
            serverName = "citadel",
            send = sent::add,
            scheduleLogin = { deferred += it },
        )

        val event = mockk<PlayerLoginSnitchEvent>()
        every { event.snitch } returns mockSnitch()
        every { event.player } returns mockPlayer()

        listener.onLogin(event)

        sent shouldBe emptyList()
        deferred shouldHaveSize 1

        deferred.single().run()

        sent shouldHaveSize 1
        (sent.single() as Payload.SnitchHit).kind shouldBe "LOGIN"
    }

    @Test
    fun `onHit does not use scheduleLogin`() {
        val sent = mutableListOf<Payload>()
        val deferred = mutableListOf<Runnable>()
        val listener = SnitchListener(
            serverName = "citadel",
            send = sent::add,
            scheduleLogin = { deferred += it },
        )

        val event = mockk<PlayerHitSnitchEvent>()
        every { event.snitch } returns mockSnitch()
        every { event.player } returns mockPlayer()

        listener.onHit(event)

        deferred shouldBe emptyList()
        sent shouldHaveSize 1
        (sent.single() as Payload.SnitchHit).kind shouldBe "ENTER"
    }

    @Test
    fun `onLogout does not use scheduleLogin`() {
        val sent = mutableListOf<Payload>()
        val deferred = mutableListOf<Runnable>()
        val listener = SnitchListener(
            serverName = "citadel",
            send = sent::add,
            scheduleLogin = { deferred += it },
        )

        val event = mockk<PlayerLogoutSnitchEvent>()
        every { event.snitch } returns mockSnitch()
        every { event.player } returns mockPlayer()

        listener.onLogout(event)

        deferred shouldBe emptyList()
        sent shouldHaveSize 1
        (sent.single() as Payload.SnitchHit).kind shouldBe "LOGOUT"
    }
}
