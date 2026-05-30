package io.github.grepsedawk.civdiscord.paper.bridge

import io.github.grepsedawk.civdiscord.core.bridge.Payload

/**
 * Every Paper-bound payload type gets a required `val` here, so a missing handler is a
 * compile error rather than a runtime no-op. Wrong-direction payloads (the ones Velocity
 * never sends to us) are handled structurally in [BridgeClient]'s `when` and never reach
 * this class.
 */
data class ClientInboundHandlers(
    val onConsoleRequest: (Payload.ConsoleRequest) -> Unit,
    val onChatToMc: (Payload.ChatToMc) -> Unit,
    val onNameLayerQuery: (Payload.NameLayerQuery) -> Unit,
    val onLinkReply: (Payload.LinkReply) -> Unit,
    val onStatusReply: (Payload.StatusReply) -> Unit,
) {
    companion object {
        /** No-op handlers for tests that don't care about dispatch. */
        fun noop(): ClientInboundHandlers = ClientInboundHandlers(
            onConsoleRequest = {},
            onChatToMc = {},
            onNameLayerQuery = {},
            onLinkReply = {},
            onStatusReply = {},
        )
    }
}
