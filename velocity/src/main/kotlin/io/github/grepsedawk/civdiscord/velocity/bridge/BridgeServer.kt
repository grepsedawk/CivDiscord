package io.github.grepsedawk.civdiscord.velocity.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel
import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.slf4j.LoggerFactory

/**
 * Server-side façade over Velocity's plugin-message channel. Production wiring:
 * - `broadcast` calls `server.getServer(targetName).get().sendPluginMessage(channel, bytes)`.
 * - `handleIncoming` is invoked from a `PluginMessageEvent` subscription.
 *
 * If a [signer] is supplied, outgoing frames are wrapped with an HMAC-SHA-256 prefix and
 * incoming frames must verify; otherwise raw BridgeCodec bytes pass through.
 */
class BridgeServer(
    private val broadcast: (target: String, bytes: ByteArray) -> Unit = { _, _ -> },
    private val signer: BridgeSigner? = null,
) {
    private val log = LoggerFactory.getLogger(BridgeServer::class.java)

    @Volatile
    var onSnitchHit: ((Payload.SnitchHit) -> Unit)? = null

    @Volatile
    var onConsoleReply: ((Payload.ConsoleReply) -> Unit)? = null

    @Volatile
    var onLinkRequest: ((Payload.LinkRequest) -> Unit)? = null

    @Volatile
    var onChatToDiscord: ((Payload.ChatToDiscord) -> Unit)? = null

    @Volatile
    var onNameLayerReply: ((Payload.NameLayerReply) -> Unit)? = null

    @Volatile
    var onStatusRequest: ((Payload.StatusRequest) -> Unit)? = null

    fun handleIncoming(bytes: ByteArray) {
        val payloadBytes = if (signer != null) signer.verify(bytes) ?: return else bytes
        val p = BridgeCodec.tryDecode(payloadBytes) ?: return
        try {
            when (p) {
                is Payload.SnitchHit -> onSnitchHit?.invoke(p)
                is Payload.ConsoleReply -> onConsoleReply?.invoke(p)
                is Payload.LinkRequest -> onLinkRequest?.invoke(p)
                is Payload.ChatToDiscord -> onChatToDiscord?.invoke(p)
                is Payload.NameLayerReply -> onNameLayerReply?.invoke(p)
                is Payload.StatusRequest -> onStatusRequest?.invoke(p)
                is Payload.ConsoleRequest -> Unit
                is Payload.LinkReply -> Unit
                is Payload.ChatToMc -> Unit
                is Payload.NameLayerQuery -> Unit
                is Payload.StatusReply -> Unit
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

    companion object {
        const val CHANNEL = BridgeChannel.NAME
    }
}
