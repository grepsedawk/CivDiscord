package io.github.grepsedawk.civdiscord.core.auth

import java.security.SecureRandom
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LinkTokenStore(
    private val ttlMs: Long = 10 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = SecureRandom(),
) {
    private val byCode = ConcurrentHashMap<String, LinkToken>()
    private val byMcUuid = ConcurrentHashMap<UUID, String>()

    fun mint(
        mcUuid: UUID,
        mcName: String,
    ): LinkToken {
        byMcUuid.remove(mcUuid)?.let { byCode.remove(it) }
        val code = generateCode()
        val token = LinkToken(code, mcUuid, mcName, clock() + ttlMs)
        byCode[code] = token
        byMcUuid[mcUuid] = code
        return token
    }

    fun consume(code: String): LinkToken? {
        val token = byCode.remove(code) ?: return null
        byMcUuid.remove(token.mcUuid, code)
        if (clock() > token.expiresAt) return null
        return token
    }

    private fun generateCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }

    companion object {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val CODE_LENGTH = 12
    }
}
