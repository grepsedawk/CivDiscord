package io.github.grepsedawk.civdiscord.velocity.commands

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatusCommandTest {
    @Test
    fun `roster shows a placeholder when nobody is online`() {
        formatRoster(emptyList()) shouldBe "_nobody online_"
    }

    @Test
    fun `roster escapes markdown in player names`() {
        formatRoster(listOf("cool_guy")) shouldBe "cool\\_guy"
    }

    @Test
    fun `roster caps the list at 50 names`() {
        val names = (1..60).map { "p$it" }
        formatRoster(names).split(", ").size shouldBe 50
    }
}
