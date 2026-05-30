package io.github.grepsedawk.civdiscord.paper.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BridgeClientTest {

    @Test
    fun `dispatch invokes the handler for the matching payload type`() {
        var got: Payload.ConsoleRequest? = null
        val client = BridgeClient(send = {})
        client.onConsoleRequest = { got = it }

        val encoded = BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi"))
        client.handleIncoming(encoded)
        got!!.command shouldBe "say hi"
    }

    @Test
    fun `send serializes and forwards via the sender lambda`() {
        var sent: ByteArray? = null
        val client = BridgeClient(send = { sent = it })
        client.send(Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"))

        val decoded = BridgeCodec.decode(sent!!) as Payload.SnitchHit
        decoded.snitchName shouldBe "S"
    }

    @Test
    fun `handler exceptions are swallowed instead of propagating to the channel loop`() {
        val client = BridgeClient(send = {})
        client.onConsoleRequest = { throw RuntimeException("boom") }

        val encoded = BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi"))
        client.handleIncoming(encoded)
    }

    @Test
    fun `onStatusReply fires on incoming StatusReply`() {
        var got: Payload.StatusReply? = null
        val client = BridgeClient(send = {})
        client.onStatusReply = { got = it }

        client.handleIncoming(
            BridgeCodec.encode(Payload.StatusReply("s-1", discordId = 7L, mcName = "alice", linkedAt = 1L)),
        )

        got!!.mcName shouldBe "alice"
    }

    @Test
    fun `signed sender produces frames that signed receiver verifies`() {
        val signer = BridgeSigner("shared-secret".toByteArray())
        var sent: ByteArray? = null
        val sender = BridgeClient(send = { sent = it }, signer = signer)
        sender.send(Payload.ConsoleRequest("r-1", "citadel", "say hi"))

        var got: Payload.ConsoleRequest? = null
        val receiver = BridgeClient(send = {}, signer = signer)
        receiver.onConsoleRequest = { got = it }
        receiver.handleIncoming(sent!!)

        got!!.command shouldBe "say hi"
    }

    @Test
    fun `signed receiver drops unsigned frames`() {
        var got: Payload.ConsoleRequest? = null
        val client = BridgeClient(send = {}, signer = BridgeSigner("k".toByteArray()))
        client.onConsoleRequest = { got = it }

        client.handleIncoming(BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi")))

        got shouldBe null
    }

    @Test
    fun `signed receiver drops frames signed with a different key`() {
        var got: Payload.ConsoleRequest? = null
        val client = BridgeClient(send = {}, signer = BridgeSigner("a".toByteArray()))
        client.onConsoleRequest = { got = it }

        val frame = BridgeSigner("b".toByteArray())
            .sign(BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi")))
        client.handleIncoming(frame)

        got shouldBe null
    }

    @Test
    fun `slow handler still completes dispatch and runs the wall-clock guard`() {
        var ran = false
        val client = BridgeClient(send = {})
        client.onConsoleRequest = {
            Thread.sleep(10)
            ran = true
        }

        val encoded = BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi"))
        client.handleIncoming(encoded)
        ran shouldBe true
    }
}
