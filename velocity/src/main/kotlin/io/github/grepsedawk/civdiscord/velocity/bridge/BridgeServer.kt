package io.github.grepsedawk.civdiscord.velocity.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel
import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.bridge.RateLimitedLogger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Server-side façade over Velocity's plugin-message channel. Production wiring:
 * - `broadcast` calls `server.getServer(targetName).get().sendPluginMessage(channel, bytes)`.
 * - `handleIncoming` is invoked from a `PluginMessageEvent` subscription.
 *
 * If a [signer] is supplied, outgoing frames are wrapped with an HMAC-SHA-256 prefix and
 * incoming frames must verify; otherwise raw BridgeCodec bytes pass through.
 *
 * Inbound handlers are passed via [handlersFactory] (called inside the constructor with `this`)
 * so every Velocity-bound payload type has a guaranteed `val` handler — a missing handler is
 * a compile error, not a silent runtime drop. Payloads Velocity only sends (never receives)
 * are routed to a named wrong-direction branch that logs + counts, instead of a silent `Unit`.
 */
class BridgeServer(
    private val broadcast: (target: String, bytes: ByteArray) -> Unit = { _, _ -> },
    private val signer: BridgeSigner? = null,
    handlersFactory: (BridgeServer) -> ServerInboundHandlers = { ServerInboundHandlers.noop() },
) {
    private val log = LoggerFactory.getLogger(BridgeServer::class.java)
    private val hmacFailLog = RateLimitedLogger(log, TimeUnit.MINUTES.toNanos(1))
    private val unknownPayloadLog = RateLimitedLogger(log, TimeUnit.MINUTES.toNanos(5))
    private val wrongDirectionLog = RateLimitedLogger(log, TimeUnit.MINUTES.toNanos(5))

    private val handlers: ServerInboundHandlers = handlersFactory(this)

    fun handleIncoming(bytes: ByteArray) {
        val payloadBytes = if (signer != null) {
            signer.verify(bytes) ?: run {
                hmacFailLog.warn(
                    "Bridge HMAC verify failed (${bytes.size}B) — check secret.key parity " +
                        "between Velocity and Paper.",
                )
                return
            }
        } else {
            bytes
        }
        val p = BridgeCodec.tryDecode(payloadBytes) ?: run {
            unknownPayloadLog.warn(
                "Bridge frame failed to decode (${payloadBytes.size}B) — possible schema skew " +
                    "during rolling deploy.",
            )
            return
        }
        try {
            when (p) {
                is Payload.SnitchHit -> handlers.onSnitchHit(p)
                is Payload.ConsoleReply -> handlers.onConsoleReply(p)
                is Payload.LinkRequest -> handlers.onLinkRequest(p)
                is Payload.ChatToDiscord -> handlers.onChatToDiscord(p)
                is Payload.NameLayerReply -> handlers.onNameLayerReply(p)
                is Payload.StatusRequest -> handlers.onStatusRequest(p)
                is Payload.ConsoleRequest,
                is Payload.LinkReply,
                is Payload.ChatToMc,
                is Payload.NameLayerQuery,
                is Payload.StatusReply,
                -> wrongDirectionLog.warn(
                    "Bridge received outbound-only payload ${p::class.simpleName} on the server " +
                        "side — Paper should never send this. Dropping.",
                )
            }
        } catch (t: Throwable) {
            log.warn("Bridge handler failed for ${p::class.simpleName}", t)
        }
    }

    fun sendToServer(server: String, p: Payload) {
        broadcast(server, frame(p))
    }

    fun sendBroadcast(p: Payload, allServers: List<String>) {
        val bytes = frame(p)
        for (s in allServers) broadcast(s, bytes)
    }

    private fun frame(p: Payload): ByteArray {
        val raw = BridgeCodec.encode(p)
        return signer?.sign(raw) ?: raw
    }

    /** Cumulative HMAC verify failures since start; for ops scraping. */
    fun hmacVerifyFailures(): Long = hmacFailLog.count()

    /** Cumulative undecodable frames since start; for ops scraping. */
    fun unknownPayloadFailures(): Long = unknownPayloadLog.count()

    /** Cumulative wrong-direction frames since start; for ops scraping. */
    fun wrongDirectionFailures(): Long = wrongDirectionLog.count()

    companion object {
        const val CHANNEL = BridgeChannel.NAME
    }
}
