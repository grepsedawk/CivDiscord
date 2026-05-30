package io.github.grepsedawk.civdiscord.paper.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel
import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.bridge.RateLimitedLogger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin façade over Paper's plugin-message channel — testable without a server.
 *
 * Production: the plugin's onEnable wires `send` to the player.sendPluginMessage call and
 * registers a Listener that forwards channel bytes to `handleIncoming`.
 *
 * A [signer] is REQUIRED for incoming frames — Bukkit's PluginMessageListener cannot
 * tell a proxy-injected frame from one a client registered on `civdiscord:bridge`, so
 * without HMAC any connected player could inject a ConsoleRequest and run server-console
 * commands. If [signer] is null, `handleIncoming` drops every frame. Outgoing frames are
 * still emitted unsigned so the proxy side can detect the misconfiguration.
 *
 * Inbound handlers are passed via [handlersFactory] (called inside the constructor with `this`)
 * so every Paper-bound payload type has a guaranteed `val` handler — a missing handler is
 * a compile error, not a silent runtime drop. Payloads Paper only sends (never receives) are
 * routed to a named wrong-direction branch that logs + counts, instead of a silent `Unit`.
 */
class BridgeClient(
    private val send: (payloadType: String, bytes: ByteArray) -> Unit,
    private val signer: BridgeSigner? = null,
    handlersFactory: (BridgeClient) -> ClientInboundHandlers = { ClientInboundHandlers.noop() },
) {

    private val log = LoggerFactory.getLogger(BridgeClient::class.java)
    private val hmacFailLog = RateLimitedLogger(log, TimeUnit.MINUTES.toNanos(1))
    private val unknownPayloadLog = RateLimitedLogger(log, TimeUnit.MINUTES.toNanos(5))
    private val wrongDirectionLog = RateLimitedLogger(log, TimeUnit.MINUTES.toNanos(5))

    private val handlers: ClientInboundHandlers = handlersFactory(this)

    fun send(p: Payload) {
        val raw = BridgeCodec.encode(p)
        send(p::class.simpleName ?: "Unknown", signer?.sign(raw) ?: raw)
    }

    fun handleIncoming(bytes: ByteArray) {
        if (signer == null) return
        val payloadBytes = signer.verify(bytes) ?: run {
            hmacFailLog.warn(
                "Bridge HMAC verify failed (${bytes.size}B) — check secret.key parity " +
                    "between Velocity and Paper.",
            )
            return
        }
        val p = BridgeCodec.tryDecode(payloadBytes) ?: run {
            unknownPayloadLog.warn(
                "Bridge frame failed to decode (${payloadBytes.size}B) — possible schema skew " +
                    "during rolling deploy.",
            )
            return
        }
        val start = System.nanoTime()
        try {
            when (p) {
                is Payload.ConsoleRequest -> handlers.onConsoleRequest(p)
                is Payload.ChatToMc -> handlers.onChatToMc(p)
                is Payload.NameLayerQuery -> handlers.onNameLayerQuery(p)
                is Payload.LinkReply -> handlers.onLinkReply(p)
                is Payload.StatusReply -> handlers.onStatusReply(p)
                is Payload.SnitchHit,
                is Payload.ConsoleReply,
                is Payload.LinkRequest,
                is Payload.ChatToDiscord,
                is Payload.NameLayerReply,
                is Payload.StatusRequest,
                -> wrongDirectionLog.warn(
                    "Bridge received outbound-only payload ${p::class.simpleName} on the client " +
                        "side — Velocity should never send this. Dropping.",
                )
            }
        } catch (t: Throwable) {
            log.warn("Bridge handler failed for ${p::class.simpleName}", t)
        } finally {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            if (elapsedMs > SLOW_HANDLER_THRESHOLD_MS) {
                log.warn("Slow bridge handler ${p::class.simpleName} took ${elapsedMs}ms")
            }
        }
    }

    /** Cumulative HMAC verify failures since start; for ops scraping. */
    fun hmacVerifyFailures(): Long = hmacFailLog.count()

    /** Cumulative undecodable frames since start; for ops scraping. */
    fun unknownPayloadFailures(): Long = unknownPayloadLog.count()

    /** Cumulative wrong-direction frames since start; for ops scraping. */
    fun wrongDirectionFailures(): Long = wrongDirectionLog.count()

    companion object {
        const val CHANNEL = BridgeChannel.NAME
        const val SLOW_HANDLER_THRESHOLD_MS = 5L
    }
}
