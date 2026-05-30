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
        val srv = BridgeServer(
            broadcast = { _, _ -> },
            handlersFactory = { ServerInboundHandlers.noop().copy(onSnitchHit = { got = it }) },
        )
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
        val srv = BridgeServer(
            broadcast = { _, _ -> },
            handlersFactory = {
                ServerInboundHandlers.noop().copy(onSnitchHit = { throw RuntimeException("boom") })
            },
        )
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
        val receiver = BridgeServer(
            broadcast = { _, _ -> },
            signer = signer,
            handlersFactory = { ServerInboundHandlers.noop().copy(onStatusRequest = { statusGot = it }) },
        )
        receiver.handleIncoming(seen.single())

        statusGot!!.mcUuid shouldBe "u"
    }

    @Test
    fun `signed receiver drops unsigned frames`() {
        val signer = BridgeSigner("k".toByteArray())
        var got: Payload.SnitchHit? = null
        val srv = BridgeServer(
            broadcast = { _, _ -> },
            signer = signer,
            handlersFactory = { ServerInboundHandlers.noop().copy(onSnitchHit = { got = it }) },
        )

        srv.handleIncoming(
            BridgeCodec.encode(
                Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
            ),
        )

        got shouldBe null
    }

    @Test
    fun `signed receiver drops frames signed with a different key`() {
        var got: Payload.SnitchHit? = null
        val srv = BridgeServer(
            broadcast = { _, _ -> },
            signer = BridgeSigner("a".toByteArray()),
            handlersFactory = { ServerInboundHandlers.noop().copy(onSnitchHit = { got = it }) },
        )

        val frame = BridgeSigner("b".toByteArray()).sign(
            BridgeCodec.encode(
                Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
            ),
        )
        srv.handleIncoming(frame)

        got shouldBe null
    }

    @Test
    fun `hmac verify failures are counted for ops scraping`() {
        val srv = BridgeServer(broadcast = { _, _ -> }, signer = BridgeSigner("a".toByteArray()))
        val frame = BridgeSigner("b".toByteArray()).sign(
            BridgeCodec.encode(
                Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
            ),
        )
        repeat(3) { srv.handleIncoming(frame) }

        srv.hmacVerifyFailures() shouldBe 3L
    }

    @Test
    fun `undecodable frames are counted separately from hmac failures`() {
        val signer = BridgeSigner("k".toByteArray())
        val srv = BridgeServer(broadcast = { _, _ -> }, signer = signer)
        srv.handleIncoming(signer.sign("not json".toByteArray()))
        srv.handleIncoming(signer.sign("{\"type\":\"future_type\"}".toByteArray()))

        srv.unknownPayloadFailures() shouldBe 2L
        srv.hmacVerifyFailures() shouldBe 0L
    }

    @Test
    fun `wrong-direction payloads are counted and never reach handlers`() {
        val signer = BridgeSigner("k".toByteArray())
        var consoleReplyGot: Payload.ConsoleReply? = null
        val srv = BridgeServer(
            broadcast = { _, _ -> },
            signer = signer,
            handlersFactory = {
                ServerInboundHandlers.noop().copy(onConsoleReply = { consoleReplyGot = it })
            },
        )

        // ConsoleRequest is outbound-only from Velocity's perspective; receiving one is wrong-direction.
        srv.handleIncoming(
            signer.sign(BridgeCodec.encode(Payload.ConsoleRequest("r-1", "citadel", "say hi"))),
        )

        consoleReplyGot shouldBe null
        srv.wrongDirectionFailures() shouldBe 1L
        srv.hmacVerifyFailures() shouldBe 0L
        srv.unknownPayloadFailures() shouldBe 0L
    }
}
