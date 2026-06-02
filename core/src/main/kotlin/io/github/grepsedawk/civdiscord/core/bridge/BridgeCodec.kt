package io.github.grepsedawk.civdiscord.core.bridge

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object BridgeCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encode(p: Payload): ByteArray = json.encodeToString(Payload.serializer(), p).toByteArray(Charsets.UTF_8)

    /** Returns null when bytes don't decode to a known Payload variant (e.g., newer peer). */
    fun tryDecode(bytes: ByteArray): Payload? = try {
        json.decodeFromString(Payload.serializer(), bytes.toString(Charsets.UTF_8))
    } catch (e: SerializationException) {
        null
    }

    /** Throws on malformed input. Kept for tests that assert round-trip correctness. */
    fun decode(bytes: ByteArray): Payload = json.decodeFromString(Payload.serializer(), bytes.toString(Charsets.UTF_8))
}
