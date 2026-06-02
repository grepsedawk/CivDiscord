package io.github.grepsedawk.civdiscord.core.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format payloads for the civdiscord:bridge plugin-message channel.
 * Encoded via BridgeCodec (kotlinx.serialization JSON, UTF-8).
 */
@Serializable
sealed class Payload {

    @Serializable
    @SerialName("snitch_hit")
    data class SnitchHit(
        val server: String,
        val snitchOwnerUuid: String,
        val intruderUuid: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val snitchName: String,
        val namelayerGroup: String,
        val kind: String,
        // Additive nullable for mixed-version rollout: older Paper omits, Velocity renders the UUID.
        val intruderName: String? = null,
    ) : Payload()

    @Serializable
    @SerialName("console_request")
    data class ConsoleRequest(
        val id: String,
        val server: String,
        val command: String,
    ) : Payload()

    @Serializable
    @SerialName("console_reply")
    data class ConsoleReply(
        val id: String,
        val ok: Boolean,
        val output: String,
    ) : Payload()

    @Serializable
    @SerialName("link_request")
    data class LinkRequest(
        val id: String,
        val mcUuid: String,
        val mcName: String,
    ) : Payload()

    @Serializable
    @SerialName("link_reply")
    data class LinkReply(
        val id: String,
        val token: String?,
        val error: String?,
    ) : Payload()

    /**
     * Discord→MC chat for a NameLayer chat group.
     *
     * @property server Target backend by Velocity server name, or "*" to fan out to every
     *   backend that knows the group. The Paper-side BridgeClient is responsible for
     *   filtering on its own server name (a server that doesn't host the group simply
     *   drops the payload). Today only "*" is emitted; per-relay targeting is a future
     *   schema addition.
     */
    @Serializable
    @SerialName("chat_to_mc")
    data class ChatToMc(
        val server: String,
        val namelayerGroup: String,
        val from: String,
        val text: String,
        // Additive for mixed-version rollout: older Velocity emits null, Paper falls back to
        // a main-thread getOfflinePlayer lookup. Once both sides are upgraded, always populated.
        val fromUuid: String? = null,
    ) : Payload()

    /**
     * MC→Discord chat for a NameLayer chat group.
     *
     * @property server Velocity server name the message originated on. Used for display
     *   context (e.g. `[citadel]` tag in rendered Discord output); not used for routing —
     *   fan-out targets are resolved from [namelayerGroup] via RelayDao.
     */
    @Serializable
    @SerialName("chat_to_discord")
    data class ChatToDiscord(
        val server: String,
        val fromUuid: String,
        val fromName: String,
        val namelayerGroup: String,
        val text: String,
    ) : Payload()

    @Serializable
    @SerialName("namelayer_query")
    data class NameLayerQuery(
        val id: String,
        val mcUuid: String,
    ) : Payload()

    @Serializable
    @SerialName("namelayer_reply")
    data class NameLayerReply(
        val id: String,
        val linkedGroups: List<String>,
    ) : Payload()

    @Serializable
    @SerialName("status_request")
    data class StatusRequest(
        val id: String,
        val mcUuid: String,
    ) : Payload()

    @Serializable
    @SerialName("status_reply")
    data class StatusReply(
        val id: String,
        val discordId: Long?,
        val mcName: String?,
        val linkedAt: Long?,
    ) : Payload()
}
