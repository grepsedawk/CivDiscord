package io.github.grepsedawk.civdiscord.paper.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BridgeClientTest {

    private val signer = BridgeSigner("shared-secret".toByteArray())

    @Test
    fun `dispatch invokes the handler for the matching payload type`() {
        var got: Payload.ConsoleRequest? = null
        val client = BridgeClient(
            send = { _, _ -> },
            signer = signer,
            handlersFactory = { ClientInboundHandlers.noop().copy(onConsoleRequest = { got = it }) },
        )

        val signed = signer.sign(BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi")))
        client.handleIncoming(signed)
        got!!.command shouldBe "say hi"
    }

    @Test
    fun `send serializes and forwards via the sender lambda`() {
        var sent: ByteArray? = null
        var seenType: String? = null
        val client = BridgeClient(
            send = { t, b ->
                seenType = t
                sent = b
            },
        )
        client.send(Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"))

        val decoded = BridgeCodec.decode(sent!!) as Payload.SnitchHit
        decoded.snitchName shouldBe "S"
        seenType shouldBe "SnitchHit"
    }

    @Test
    fun `handler exceptions are swallowed instead of propagating to the channel loop`() {
        val client = BridgeClient(
            send = { _, _ -> },
            signer = signer,
            handlersFactory = {
                ClientInboundHandlers.noop().copy(onConsoleRequest = { throw RuntimeException("boom") })
            },
        )

        val signed = signer.sign(BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi")))
        client.handleIncoming(signed)
    }

    @Test
    fun `onStatusReply fires on incoming StatusReply`() {
        var got: Payload.StatusReply? = null
        val client = BridgeClient(
            send = { _, _ -> },
            signer = signer,
            handlersFactory = { ClientInboundHandlers.noop().copy(onStatusReply = { got = it }) },
        )

        client.handleIncoming(
            signer.sign(
                BridgeCodec.encode(Payload.StatusReply("s-1", discordId = 7L, mcName = "alice", linkedAt = 1L)),
            ),
        )

        got!!.mcName shouldBe "alice"
    }

    @Test
    fun `signed sender produces frames that signed receiver verifies`() {
        var sent: ByteArray? = null
        val sender = BridgeClient(send = { _, b -> sent = b }, signer = signer)
        sender.send(Payload.ConsoleRequest("r-1", "citadel", "say hi"))

        var got: Payload.ConsoleRequest? = null
        val receiver = BridgeClient(
            send = { _, _ -> },
            signer = signer,
            handlersFactory = { ClientInboundHandlers.noop().copy(onConsoleRequest = { got = it }) },
        )
        receiver.handleIncoming(sent!!)

        got!!.command shouldBe "say hi"
    }

    @Test
    fun `signed receiver drops unsigned frames`() {
        var got: Payload.ConsoleRequest? = null
        val client = BridgeClient(
            send = { _, _ -> },
            signer = BridgeSigner("k".toByteArray()),
            handlersFactory = { ClientInboundHandlers.noop().copy(onConsoleRequest = { got = it }) },
        )

        client.handleIncoming(BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi")))

        got shouldBe null
    }

    @Test
    fun `signed receiver drops frames signed with a different key`() {
        var got: Payload.ConsoleRequest? = null
        val client = BridgeClient(
            send = { _, _ -> },
            signer = BridgeSigner("a".toByteArray()),
            handlersFactory = { ClientInboundHandlers.noop().copy(onConsoleRequest = { got = it }) },
        )

        val frame = BridgeSigner("b".toByteArray())
            .sign(BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi")))
        client.handleIncoming(frame)

        got shouldBe null
    }

    @Test
    fun `unsigned receiver drops every incoming frame fail-closed`() {
        var got: Payload.ConsoleRequest? = null
        val client = BridgeClient(
            send = { _, _ -> },
            signer = null,
            handlersFactory = { ClientInboundHandlers.noop().copy(onConsoleRequest = { got = it }) },
        )

        val raw = BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi"))
        client.handleIncoming(raw)
        client.handleIncoming(signer.sign(raw))

        got shouldBe null
    }

    @Test
    fun `slow handler still completes dispatch and runs the wall-clock guard`() {
        var ran = false
        val client = BridgeClient(
            send = { _, _ -> },
            signer = signer,
            handlersFactory = {
                ClientInboundHandlers.noop().copy(
                    onConsoleRequest = {
                        Thread.sleep(10)
                        ran = true
                    },
                )
            },
        )

        val signed = signer.sign(BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi")))
        client.handleIncoming(signed)
        ran shouldBe true
    }

    @Test
    fun `wrong-direction payloads are counted and never reach handlers`() {
        var statusReplyGot: Payload.StatusReply? = null
        val client = BridgeClient(
            send = { _, _ -> },
            signer = signer,
            handlersFactory = {
                ClientInboundHandlers.noop().copy(onStatusReply = { statusReplyGot = it })
            },
        )

        // SnitchHit is outbound-only from Paper's perspective; receiving one is wrong-direction.
        client.handleIncoming(
            signer.sign(
                BridgeCodec.encode(
                    Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
                ),
            ),
        )

        statusReplyGot shouldBe null
        client.wrongDirectionFailures() shouldBe 1L
        client.hmacVerifyFailures() shouldBe 0L
        client.unknownPayloadFailures() shouldBe 0L
    }
}
