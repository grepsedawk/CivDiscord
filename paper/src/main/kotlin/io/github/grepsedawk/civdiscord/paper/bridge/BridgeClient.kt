package io.github.grepsedawk.civdiscord.paper.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel
import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.slf4j.LoggerFactory

/**
 * Pure-Kotlin façade over Paper's plugin-message channel — testable without a server.
 *
 * Production: the plugin's onEnable wires `send` to the player.sendPluginMessage call and
 * registers a Listener that forwards channel bytes to `handleIncoming`.
 *
 * If a [signer] is supplied, outgoing frames are wrapped with an HMAC-SHA-256 prefix and
 * incoming frames must verify; otherwise raw BridgeCodec bytes pass through.
 */
class BridgeClient(
    private val send: (ByteArray) -> Unit,
    private val signer: BridgeSigner? = null,
) {

    private val log = LoggerFactory.getLogger(BridgeClient::class.java)

    @Volatile
    var onConsoleRequest: ((Payload.ConsoleRequest) -> Unit)? = null

    @Volatile
    var onChatToMc: ((Payload.ChatToMc) -> Unit)? = null

    @Volatile
    var onNameLayerQuery: ((Payload.NameLayerQuery) -> Unit)? = null

    @Volatile
    var onLinkReply: ((Payload.LinkReply) -> Unit)? = null

    @Volatile
    var onStatusReply: ((Payload.StatusReply) -> Unit)? = null

    fun send(p: Payload) {
        val raw = BridgeCodec.encode(p)
        send(signer?.sign(raw) ?: raw)
    }

    fun handleIncoming(bytes: ByteArray) {
        val payloadBytes = if (signer != null) signer.verify(bytes) ?: return else bytes
        val p = BridgeCodec.tryDecode(payloadBytes) ?: return
        val start = System.nanoTime()
        try {
            when (p) {
                is Payload.ConsoleRequest -> onConsoleRequest?.invoke(p)
                is Payload.ChatToMc -> onChatToMc?.invoke(p)
                is Payload.NameLayerQuery -> onNameLayerQuery?.invoke(p)
                is Payload.LinkReply -> onLinkReply?.invoke(p)
                is Payload.StatusReply -> onStatusReply?.invoke(p)
                is Payload.SnitchHit -> Unit
                is Payload.ConsoleReply -> Unit
                is Payload.LinkRequest -> Unit
                is Payload.ChatToDiscord -> Unit
                is Payload.NameLayerReply -> Unit
                is Payload.StatusRequest -> Unit
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

    companion object {
        const val CHANNEL = BridgeChannel.NAME
        const val SLOW_HANDLER_THRESHOLD_MS = 5L
    }
}
