package io.github.grepsedawk.civdiscord.core.patreon

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TierRoleMapTest {
    @Test
    fun `roleForTier returns mapped role`() {
        val m = TierRoleMap(mapOf("gold" to 11L, "silver" to 22L))
        m.roleForTier("gold") shouldBe 11L
    }

    @Test
    fun `roleForTier returns null for unknown tier`() {
        TierRoleMap(emptyMap()).roleForTier("none") shouldBe null
    }

    @Test
    fun `allRoleIds returns every mapped role id`() {
        val m = TierRoleMap(mapOf("gold" to 11L, "silver" to 22L))
        m.allRoleIds() shouldContainExactlyInAnyOrder listOf(11L, 22L)
    }
}
