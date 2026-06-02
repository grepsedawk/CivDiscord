package io.github.grepsedawk.civdiscord.core.feed

import io.github.grepsedawk.civdiscord.core.db.LoginLogoutFeedDao

class LoginLogoutFeedService(private val dao: LoginLogoutFeedDao) {
    // channelId() is read on the proxy event thread for every connection event; bind/unbind
    // write from the JDA command thread. @Volatile carries that cross-thread visibility, and
    // the cache stays correct because this is the only writer of the single feed row.
    @Volatile
    private var cachedChannelId: Long? = dao.get()?.channelId

    sealed class BindResult {
        data object Bound : BindResult()
        data class AlreadyBound(val channelId: Long) : BindResult()
    }

    sealed class UnbindResult {
        data object Unbound : UnbindResult()
        data object NotBound : UnbindResult()
    }

    fun bind(guildId: Long, channelId: Long, createdBy: Long): BindResult = when (dao.bind(guildId, channelId, createdBy)) {
        LoginLogoutFeedDao.BindOutcome.Inserted -> {
            cachedChannelId = channelId
            BindResult.Bound
        }
        LoginLogoutFeedDao.BindOutcome.AlreadyBound ->
            BindResult.AlreadyBound(cachedChannelId ?: dao.get()?.channelId ?: channelId)
    }

    fun unbind(): UnbindResult = if (dao.unbind()) {
        cachedChannelId = null
        UnbindResult.Unbound
    } else {
        UnbindResult.NotBound
    }

    fun channelId(): Long? = cachedChannelId
}
