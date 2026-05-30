package io.github.grepsedawk.civdiscord.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SmokeTest {
    @Test
    fun `kotlin and kotest assertions are wired`() {
        (1 + 1) shouldBe 2
    }
}
