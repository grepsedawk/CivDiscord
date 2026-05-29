package io.github.grepsedawk.civdiscord.paper.jukealert

import com.untamedears.jukealert.events.PlayerHitSnitchEvent
import com.untamedears.jukealert.events.PlayerLoginSnitchEvent
import com.untamedears.jukealert.events.PlayerLogoutSnitchEvent
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.junit.jupiter.api.Test
import java.util.UUID

class SnitchListenerTest {

    private val intruder: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val owner: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

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
        return p
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
}
