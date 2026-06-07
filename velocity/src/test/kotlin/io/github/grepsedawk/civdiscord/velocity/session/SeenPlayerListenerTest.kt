package io.github.grepsedawk.civdiscord.velocity.session

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class SeenPlayerListenerTest {
    @Test
    fun `record forwards uuid and timestamp to the sink`() {
        val seen = mutableListOf<Pair<String, Long>>()
        val listener = SeenPlayerListener(record = { uuid, epoch -> seen += uuid to epoch }, now = { 1234 })
        listener.record("uuid-x")
        seen.shouldContainExactly(listOf("uuid-x" to 1234L))
    }
}
