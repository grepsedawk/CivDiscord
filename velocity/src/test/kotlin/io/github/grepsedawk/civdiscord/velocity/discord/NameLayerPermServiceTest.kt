package io.github.grepsedawk.civdiscord.velocity.discord

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class NameLayerPermServiceTest {

    private val uuid = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")

    private class Lookup(private val table: Map<Triple<UUID, String, String>, PermCheck>) {
        val calls = mutableListOf<Triple<UUID, String, String>>()

        fun fn(): (UUID, String, String) -> PermCheck = { u, g, p ->
            val key = Triple(u, g, p)
            calls += key
            table[key] ?: PermCheck.DENIED
        }
    }

    @Test
    fun `hasPerm delegates to lookup and returns its result`() {
        val l = Lookup(mapOf(Triple(uuid, "townhall", "READ_CHAT") to PermCheck.ALLOWED))
        val svc = NameLayerPermService(lookup = l.fn())
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        l.calls shouldBe listOf(Triple(uuid, "townhall", "READ_CHAT"))
    }

    @Test
    fun `hasPerm returns false when lookup denies`() {
        val l = Lookup(emptyMap())
        val svc = NameLayerPermService(lookup = l.fn())
        svc.hasPerm(uuid, "townhall", "SNITCH_NOTIFICATIONS") shouldBe false
    }

    @Test
    fun `hasPerm returns false when lookup is UNKNOWN`() {
        val l = Lookup(mapOf(Triple(uuid, "townhall", "READ_CHAT") to PermCheck.UNKNOWN))
        val svc = NameLayerPermService(lookup = l.fn())
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe false
        svc.check(uuid, "townhall", "READ_CHAT") shouldBe PermCheck.UNKNOWN
    }

    @Test
    fun `cache hit within TTL skips second lookup`() {
        val l = Lookup(mapOf(Triple(uuid, "townhall", "READ_CHAT") to PermCheck.ALLOWED))
        val now = AtomicLong(0L)
        val svc = NameLayerPermService(lookup = l.fn(), nowMillis = { now.get() })
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        now.set(30_000)
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        l.calls.size shouldBe 1
    }

    @Test
    fun `cache miss after TTL re-runs lookup`() {
        val l = Lookup(mapOf(Triple(uuid, "townhall", "READ_CHAT") to PermCheck.ALLOWED))
        val now = AtomicLong(0L)
        val svc = NameLayerPermService(lookup = l.fn(), nowMillis = { now.get() })
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        now.set(61_000)
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        l.calls.size shouldBe 2
    }

    @Test
    fun `UNKNOWN is cached only briefly then retried`() {
        // A failed lookup is cached just long enough to stop a per-message flood from
        // hammering the pool during an outage, but far shorter than a real result so
        // relays resume within seconds of the DB recovering.
        val results = ArrayDeque(listOf(PermCheck.UNKNOWN, PermCheck.ALLOWED))
        var calls = 0
        val now = AtomicLong(0L)
        val svc = NameLayerPermService(
            lookup = { _, _, _ ->
                calls++
                results.removeFirst()
            },
            nowMillis = { now.get() },
            unknownTtlMillis = 5_000L,
        )
        svc.check(uuid, "townhall", "READ_CHAT") shouldBe PermCheck.UNKNOWN
        now.set(4_000)
        svc.check(uuid, "townhall", "READ_CHAT") shouldBe PermCheck.UNKNOWN
        calls shouldBe 1
        now.set(6_000)
        svc.check(uuid, "townhall", "READ_CHAT") shouldBe PermCheck.ALLOWED
        calls shouldBe 2
    }

    @Test
    fun `relayReadDecision maps binder state to a relay action`() {
        val svc = NameLayerPermService(
            lookup = { _, g, _ ->
                when (g) {
                    "allow" -> PermCheck.ALLOWED
                    "deny" -> PermCheck.DENIED
                    else -> PermCheck.UNKNOWN
                }
            },
        )
        svc.relayReadDecision(uuid, "allow") shouldBe RelayDecision.RELAY
        svc.relayReadDecision(uuid, "deny") shouldBe RelayDecision.UNBIND
        svc.relayReadDecision(uuid, "down") shouldBe RelayDecision.SKIP
        svc.relayReadDecision(null, "allow") shouldBe RelayDecision.UNBIND
    }

    @Test
    fun `cache key includes perm so different perms do not collide`() {
        val l = Lookup(
            mapOf(
                Triple(uuid, "townhall", "READ_CHAT") to PermCheck.ALLOWED,
                Triple(uuid, "townhall", "SNITCH_NOTIFICATIONS") to PermCheck.DENIED,
            ),
        )
        val svc = NameLayerPermService(lookup = l.fn())
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        svc.hasPerm(uuid, "townhall", "SNITCH_NOTIFICATIONS") shouldBe false
        l.calls.size shouldBe 2
    }
}
