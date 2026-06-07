package io.github.grepsedawk.civdiscord.paper.metrics

import io.github.grepsedawk.civdiscord.core.bridge.Payload

/** Pushes this backend's TPS + uptime to the proxy. Bukkit access is injected so the build is
 *  unit-testable. Sends nothing when empty: plugin messaging needs a carrier player, and stale
 *  metrics for an empty server are useless (the proxy shows count 0 / TPS dash). */
class MetricsPublisher(
    private val serverName: String,
    private val tps: () -> DoubleArray,
    private val onlineCount: () -> Int,
    private val uptimeSeconds: () -> Long,
    private val send: (Payload) -> Unit,
) {
    fun tick() {
        val online = onlineCount()
        if (online <= 0) return
        val t = tps()
        // Don't fabricate a healthy 20.0 from a missing reading — that would paint an
        // unknown-TPS server green. A real measurement is required to report at all.
        if (t.isEmpty()) return
        send(
            Payload.ServerMetrics(
                server = serverName,
                tps1m = t[0],
                onlineOnBackend = online,
                backendUptimeSeconds = uptimeSeconds(),
            ),
        )
    }
}
