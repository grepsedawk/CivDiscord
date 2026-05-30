package io.github.grepsedawk.civdiscord.core.bridge

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Wraps BridgeCodec.encode/decode with an HMAC-SHA-256 frame prefix.
 * Wire format: [32-byte HMAC][JSON payload bytes].
 *
 * Both sides must share the same key. Mismatched key → verify returns null (peer drops frame).
 */
class BridgeSigner(private val secret: ByteArray) {

    fun sign(payloadBytes: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
        val hmac = mac.doFinal(payloadBytes)
        return hmac + payloadBytes
    }

    /** Returns the unwrapped payload bytes if signature matches, or null. */
    fun verify(frame: ByteArray): ByteArray? {
        if (frame.size < HMAC_SIZE) return null
        val hmac = frame.copyOfRange(0, HMAC_SIZE)
        val payload = frame.copyOfRange(HMAC_SIZE, frame.size)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
        val expected = mac.doFinal(payload)
        return if (constantTimeEquals(hmac, expected)) payload else null
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    companion object {
        const val HMAC_SIZE = 32
    }
}
