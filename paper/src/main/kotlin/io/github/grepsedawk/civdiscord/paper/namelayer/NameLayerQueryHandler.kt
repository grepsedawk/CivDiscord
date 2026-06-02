package io.github.grepsedawk.civdiscord.paper.namelayer

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import java.util.UUID

class NameLayerQueryHandler(
    private val resolver: (UUID) -> List<String>,
    private val send: (Payload) -> Unit,
) {
    fun handle(q: Payload.NameLayerQuery) {
        val groups = try {
            resolver(UUID.fromString(q.mcUuid))
        } catch (e: Exception) {
            emptyList()
        }
        send(Payload.NameLayerReply(q.id, groups))
    }
}
