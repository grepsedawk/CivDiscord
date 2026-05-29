package io.github.grepsedawk.civdiscord.velocity.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel

class BridgeMessageRouter(private val bridge: BridgeServer) {
    /**
     * Returns true if the channel matched, so the @Subscribe handler can mark the event handled.
     *
     * [fromBackend] must be true (caller checks `event.source is ServerConnection`); player-sourced
     * frames on the bridge channel are dropped unconditionally to prevent a client from forging
     * snitch/chat/link payloads.
     */
    fun route(channel: String, data: ByteArray, fromBackend: Boolean = false): Boolean {
        if (channel != BridgeChannel.NAME) return false
        if (!fromBackend) return false
        bridge.handleIncoming(data)
        return true
    }
}
