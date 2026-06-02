// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Ally Piechowski (grepsedawk)
package io.github.grepsedawk.civdiscord.velocity.discord

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class WriterlessChannelNotifierTest {

    @Test
    fun `first call emits, second within window suppresses`() {
        val sent = mutableListOf<Pair<Long, String>>()
        val now = AtomicLong(0)
        val n = WriterlessChannelNotifier(nowMs = { now.get() }, windowMs = 60_000, send = { id, text -> sent += id to text })
        n.notify(channelId = 99, bound = listOf("townhall", "tavern"))
        n.notify(channelId = 99, bound = listOf("townhall", "tavern"))
        sent.size shouldBe 1
        sent[0].first shouldBe 99L
        sent[0].second.contains("/relay writer") shouldBe true
        sent[0].second.contains("townhall") shouldBe true
        sent[0].second.contains("tavern") shouldBe true
    }

    @Test
    fun `second call after window emits again`() {
        val sent = mutableListOf<Pair<Long, String>>()
        val now = AtomicLong(0)
        val n = WriterlessChannelNotifier(nowMs = { now.get() }, windowMs = 60_000, send = { id, text -> sent += id to text })
        n.notify(99, listOf("a"))
        now.set(61_000)
        n.notify(99, listOf("a"))
        sent.size shouldBe 2
    }

    @Test
    fun `different channels do not share a window`() {
        val sent = mutableListOf<Pair<Long, String>>()
        val now = AtomicLong(0)
        val n = WriterlessChannelNotifier(nowMs = { now.get() }, windowMs = 60_000, send = { id, text -> sent += id to text })
        n.notify(99, listOf("a"))
        n.notify(100, listOf("a"))
        sent.size shouldBe 2
    }

    @Test
    fun `empty bound list is rejected`() {
        val n = WriterlessChannelNotifier(nowMs = { 0 }, windowMs = 60_000, send = { _, _ -> })
        shouldThrow<IllegalArgumentException> { n.notify(99, emptyList()) }
    }
}
