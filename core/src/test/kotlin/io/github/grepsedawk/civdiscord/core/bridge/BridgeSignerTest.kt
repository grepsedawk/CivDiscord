package io.github.grepsedawk.civdiscord.core.bridge

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class BridgeSignerTest {

    private val secret = "test-secret-key-32-bytes-long-pad".toByteArray()

    @Test
    fun `sign then verify returns the original payload`() {
        val signer = BridgeSigner(secret)
        val payload = "hello world".toByteArray()

        val signed = signer.sign(payload)
        signer.verify(signed)?.toString(Charsets.UTF_8) shouldBe "hello world"
    }

    @Test
    fun `verify returns null when payload bytes are tampered`() {
        val signer = BridgeSigner(secret)
        val payload = "hello world".toByteArray()

        val signed = signer.sign(payload).copyOf()
        signed[signed.size - 1] = (signed[signed.size - 1] + 1).toByte()

        signer.verify(signed) shouldBe null
    }

    @Test
    fun `verify returns null when HMAC bytes are tampered`() {
        val signer = BridgeSigner(secret)
        val payload = "hello world".toByteArray()

        val signed = signer.sign(payload).copyOf()
        signed[0] = (signed[0] + 1).toByte()

        signer.verify(signed) shouldBe null
    }

    @Test
    fun `verify returns null when secret differs`() {
        val signer = BridgeSigner(secret)
        val other = BridgeSigner("different-secret".toByteArray())
        val payload = "hello world".toByteArray()

        other.verify(signer.sign(payload)) shouldBe null
    }

    @Test
    fun `verify returns null on undersized frame`() {
        val signer = BridgeSigner(secret)
        signer.verify(ByteArray(10)) shouldBe null
    }

    @Test
    fun `signed frame has 32-byte HMAC prefix`() {
        val signer = BridgeSigner(secret)
        val payload = "x".toByteArray()
        signer.sign(payload).size shouldBe 32 + 1
    }

    @Test
    fun `bridge codec round-trip survives signing`() {
        val signer = BridgeSigner(secret)
        val p = Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER")
        val signed = signer.sign(BridgeCodec.encode(p))
        val unwrapped = signer.verify(signed)
        unwrapped shouldNotBe null
        BridgeCodec.decode(unwrapped!!) shouldBe p
    }
}
