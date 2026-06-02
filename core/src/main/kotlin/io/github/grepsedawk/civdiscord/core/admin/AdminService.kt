package io.github.grepsedawk.civdiscord.core.admin

import io.github.grepsedawk.civdiscord.core.db.Binding
import io.github.grepsedawk.civdiscord.core.db.BindingDao

class AdminService(private val bindings: BindingDao) {

    sealed class UnlinkResult {
        data object Unlinked : UnlinkResult()
        data object NotLinked : UnlinkResult()
    }

    fun forceUnlink(discordId: Long): UnlinkResult = if (bindings.delete(discordId)) UnlinkResult.Unlinked else UnlinkResult.NotLinked

    fun viewBinding(discordId: Long): Binding? = bindings.findByDiscordId(discordId)
}
