package io.github.grepsedawk.civdiscord.velocity.session

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class ConnectionTrackerTest {
    private val alice = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val bob = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `first connect is a login`() {
        ConnectionTracker().connected(alice, "eden") shouldBe Transition.Login("eden")
    }

    @Test
    fun `connect to a different server is a switch`() {
        val t = ConnectionTracker()
        t.connected(alice, "eden")
        t.connected(alice, "minigames") shouldBe Transition.Switch("eden", "minigames")
    }

    @Test
    fun `connect to the same server is suppressed`() {
        val t = ConnectionTracker()
        t.connected(alice, "eden")
        t.connected(alice, "eden") shouldBe Transition.None
    }

    @Test
    fun `disconnect after a known server is a logout`() {
        val t = ConnectionTracker()
        t.connected(alice, "eden")
        t.disconnected(alice) shouldBe Transition.Logout("eden")
    }

    @Test
    fun `disconnect with no known server is suppressed`() {
        ConnectionTracker().disconnected(alice) shouldBe Transition.None
    }

    @Test
    fun `reconnect after logout is a fresh login`() {
        val t = ConnectionTracker()
        t.connected(alice, "eden")
        t.disconnected(alice)
        t.connected(alice, "end") shouldBe Transition.Login("end")
    }

    @Test
    fun `players are tracked independently`() {
        val t = ConnectionTracker()
        t.connected(alice, "eden")
        t.connected(bob, "minigames") shouldBe Transition.Login("minigames")
        t.disconnected(alice) shouldBe Transition.Logout("eden")
    }
}
