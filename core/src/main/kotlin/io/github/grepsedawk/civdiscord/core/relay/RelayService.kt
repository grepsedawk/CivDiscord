package io.github.grepsedawk.civdiscord.core.relay

import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao

class RelayService(private val dao: RelayDao) {

    sealed class BindResult {
        data object Bound : BindResult()
        data object ChannelAlreadyBound : BindResult()
    }

    sealed class UnbindResult {
        data object Unbound : UnbindResult()
        data object NotBound : UnbindResult()
    }

    sealed class SetResult {
        data object Updated : SetResult()
        data object NotBound : SetResult()
    }

    fun bind(guildId: Long, channelId: Long, group: String, createdBy: Long): BindResult =
        when (dao.bind(guildId, channelId, group, createdBy)) {
            is RelayDao.BindOutcome.Inserted -> BindResult.Bound
            RelayDao.BindOutcome.AlreadyBound -> BindResult.ChannelAlreadyBound
        }

    fun unbind(channelId: Long): UnbindResult {
        return if (dao.unbind(channelId)) UnbindResult.Unbound else UnbindResult.NotBound
    }

    fun listForGuild(guildId: Long): List<Relay> = dao.listForGuild(guildId)

    fun findByChannel(channelId: Long): Relay? = dao.findByChannel(channelId)

    fun setShowSnitches(channelId: Long, value: Boolean): SetResult =
        if (dao.setShowSnitches(channelId, value) > 0) SetResult.Updated else SetResult.NotBound

    fun setChatFormat(channelId: Long, value: String?): SetResult =
        if (dao.setChatFormat(channelId, value) > 0) SetResult.Updated else SetResult.NotBound
}
