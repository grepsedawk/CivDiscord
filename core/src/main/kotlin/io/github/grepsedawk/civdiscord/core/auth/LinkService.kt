package io.github.grepsedawk.civdiscord.core.auth

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.LinkOutcome
import java.util.UUID

class LinkService(
    private val tokens: LinkTokenStore,
    private val bindings: BindingDao,
) {
    sealed class Result {
        data class Linked(val mcUuid: UUID, val mcName: String, val replaced: Boolean) : Result()

        data class McAlreadyLinked(val otherDiscordId: Long) : Result()

        data object NoSuchCode : Result()
    }

    fun redeem(
        discordId: Long,
        code: String,
    ): Result {
        val token = tokens.consume(code) ?: return Result.NoSuchCode
        return when (val outcome = bindings.upsert(discordId, token.mcUuid, token.mcName)) {
            is LinkOutcome.Linked ->
                Result.Linked(token.mcUuid, token.mcName, replaced = outcome.replaced)
            is LinkOutcome.McAlreadyLinkedTo ->
                Result.McAlreadyLinked(outcome.otherDiscordId)
        }
    }
}
