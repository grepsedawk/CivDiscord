package io.github.grepsedawk.civdiscord.velocity.bridge

import io.github.grepsedawk.civdiscord.core.bridge.Payload

/**
 * Every Velocity-bound payload type gets a required `val` here, so a missing handler is a
 * compile error rather than a runtime no-op. Wrong-direction payloads (the ones Paper never
 * sends to us) are handled structurally in [BridgeServer]'s `when` and never reach this class.
 */
data class ServerInboundHandlers(
    val onSnitchHit: (Payload.SnitchHit) -> Unit,
    val onConsoleReply: (Payload.ConsoleReply) -> Unit,
    val onLinkRequest: (Payload.LinkRequest) -> Unit,
    val onChatToDiscord: (Payload.ChatToDiscord) -> Unit,
    val onNameLayerReply: (Payload.NameLayerReply) -> Unit,
    val onStatusRequest: (Payload.StatusRequest) -> Unit,
) {
    companion object {
        /** No-op handlers for tests that don't care about dispatch. */
        fun noop(): ServerInboundHandlers = ServerInboundHandlers(
            onSnitchHit = {},
            onConsoleReply = {},
            onLinkRequest = {},
            onChatToDiscord = {},
            onNameLayerReply = {},
            onStatusRequest = {},
        )
    }
}
