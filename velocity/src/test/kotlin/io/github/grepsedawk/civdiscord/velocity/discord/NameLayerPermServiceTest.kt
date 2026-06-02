package io.github.grepsedawk.civdiscord.velocity.discord

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class NameLayerPermServiceTest {

    private val uuid = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")

    private class Lookup(private val table: Map<Triple<UUID, String, String>, Boolean>) {
        val calls = mutableListOf<Triple<UUID, String, String>>()

        fun fn(): (UUID, String, String) -> Boolean = { u, g, p ->
            val key = Triple(u, g, p)
            calls += key
            table[key] ?: false
        }
    }

    @Test
    fun `hasPerm delegates to lookup and returns its result`() {
        val l = Lookup(mapOf(Triple(uuid, "townhall", "READ_CHAT") to true))
        val svc = NameLayerPermService(lookup = l.fn())
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        l.calls shouldBe listOf(Triple(uuid, "townhall", "READ_CHAT"))
    }

    @Test
    fun `hasPerm returns false when lookup returns false`() {
        val l = Lookup(emptyMap())
        val svc = NameLayerPermService(lookup = l.fn())
        svc.hasPerm(uuid, "townhall", "SNITCH_NOTIFICATIONS") shouldBe false
    }

    @Test
    fun `cache hit within TTL skips second lookup`() {
        val l = Lookup(mapOf(Triple(uuid, "townhall", "READ_CHAT") to true))
        val now = AtomicLong(0L)
        val svc = NameLayerPermService(lookup = l.fn(), nowMillis = { now.get() })
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        now.set(30_000)
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        l.calls.size shouldBe 1
    }

    @Test
    fun `cache miss after TTL re-runs lookup`() {
        val l = Lookup(mapOf(Triple(uuid, "townhall", "READ_CHAT") to true))
        val now = AtomicLong(0L)
        val svc = NameLayerPermService(lookup = l.fn(), nowMillis = { now.get() })
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        now.set(61_000)
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        l.calls.size shouldBe 2
    }

    @Test
    fun `cache key includes perm so different perms do not collide`() {
        val l = Lookup(
            mapOf(
                Triple(uuid, "townhall", "READ_CHAT") to true,
                Triple(uuid, "townhall", "SNITCH_NOTIFICATIONS") to false,
            ),
        )
        val svc = NameLayerPermService(lookup = l.fn())
        svc.hasPerm(uuid, "townhall", "READ_CHAT") shouldBe true
        svc.hasPerm(uuid, "townhall", "SNITCH_NOTIFICATIONS") shouldBe false
        l.calls.size shouldBe 2
    }
}
