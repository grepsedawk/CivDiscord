// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Ally Piechowski (grepsedawk)
package io.github.grepsedawk.civdiscord.velocity.discord

import java.util.concurrent.ConcurrentHashMap

/**
 * Emits a public (non-ephemeral) hint into a Discord channel that has bindings
 * but no writer, at most once per `windowMs` per channel.
 */
class WriterlessChannelNotifier(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val windowMs: Long = 60_000L,
    private val send: (channelId: Long, text: String) -> Unit,
) {
    // One entry per Discord channel ever seen. Channel cardinality is small
    // (a handful per guild) so unbounded growth is fine in practice.
    private val lastSentAt = ConcurrentHashMap<Long, Long>()

    fun notify(channelId: Long, bound: List<String>) {
        require(bound.isNotEmpty()) { "bound must be non-empty" }
        val now = nowMs()
        // compute() makes the read-modify-write atomic per key so two concurrent
        // callers on the same channel can't both pass the rate-limit check.
        var shouldSend = false
        lastSentAt.compute(channelId) { _, prior ->
            if (prior != null && now - prior < windowMs) {
                prior
            } else {
                shouldSend = true
                now
            }
        }
        if (!shouldSend) return
        val list = bound.joinToString(", ") { "`$it`" }
        send(
            channelId,
            "No writer set for this channel. Use /relay writer <group> on a bound " +
                "NameLayer group to designate one. Bound: $list.",
        )
    }
}
