package io.github.grepsedawk.civdiscord.velocity.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BridgeServerTest {

    @Test
    fun `dispatch fires the matching handler`() {
        var got: Payload.SnitchHit? = null
        val srv = BridgeServer(broadcast = { _, _ -> })
        srv.onSnitchHit = { got = it }
        srv.handleIncoming(
            BridgeCodec.encode(
                Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
            ),
        )
        got!!.snitchName shouldBe "S"
    }

    @Test
    fun `send serializes and forwards to broadcast`() {
        val seen = mutableListOf<ByteArray>()
        val srv = BridgeServer(broadcast = { _, bytes -> seen.add(bytes) })
        srv.sendToServer("citadel", Payload.ConsoleRequest("r1", "citadel", "version"))
        seen.size shouldBe 1
    }

    @Test
    fun `handler exceptions are swallowed instead of propagating to the JDA listener thread`() {
        val srv = BridgeServer(broadcast = { _, _ -> })
        srv.onSnitchHit = { throw RuntimeException("boom") }
        srv.handleIncoming(
            BridgeCodec.encode(
                Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
            ),
        )
    }

    @Test
    fun `signed sender produces frames that signed receiver verifies`() {
        val signer = BridgeSigner("shared-secret".toByteArray())
        val seen = mutableListOf<ByteArray>()
        val sender = BridgeServer(broadcast = { _, bytes -> seen.add(bytes) }, signer = signer)
        sender.sendToServer("citadel", Payload.StatusRequest("s-1", "u"))

        var statusGot: Payload.StatusRequest? = null
        val receiver = BridgeServer(broadcast = { _, _ -> }, signer = signer)
        receiver.onStatusRequest = { statusGot = it }
        receiver.handleIncoming(seen.single())

        statusGot!!.mcUuid shouldBe "u"
    }

    @Test
    fun `signed receiver drops unsigned frames`() {
        val signer = BridgeSigner("k".toByteArray())
        var got: Payload.SnitchHit? = null
        val srv = BridgeServer(broadcast = { _, _ -> }, signer = signer)
        srv.onSnitchHit = { got = it }

        srv.handleIncoming(
            BridgeCodec.encode(
                Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
            ),
        )

        got shouldBe null
    }

    @Test
    fun `signed receiver drops frames signed with a different key`() {
        val srv = BridgeServer(broadcast = { _, _ -> }, signer = BridgeSigner("a".toByteArray()))
        var got: Payload.SnitchHit? = null
        srv.onSnitchHit = { got = it }

        val frame = BridgeSigner("b".toByteArray()).sign(
            BridgeCodec.encode(
                Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
            ),
        )
        srv.handleIncoming(frame)

        got shouldBe null
    }
}
