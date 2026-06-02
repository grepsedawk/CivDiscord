package io.github.grepsedawk.civdiscord.core.relay

import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao

class RelayService(private val dao: RelayDao) {

    sealed class BindResult {
        data object Writer : BindResult()
        data object Reader : BindResult()
        data object ChannelAlreadyBound : BindResult()
    }

    sealed class UnbindResult {
        data object Unbound : UnbindResult()
        data object NotBound : UnbindResult()
    }

    sealed class PromoteWriterResult {
        data object Promoted : PromoteWriterResult()
        data object NotBound : PromoteWriterResult()
    }

    sealed class SetResult {
        data object Updated : SetResult()
        data object NotBound : SetResult()
    }

    /**
     * Caller is trusted on [showSnitches] — the gate against SNITCH_NOTIFICATIONS lives in
     * the command layer (`/relay set show-snitches`). A reader that supplies `true` here
     * bypasses that gate; only call sites that have already verified the permission should
     * pass `true`.
     *
     * [listForChannel] and [dao.bind] aren't a single transaction, so two concurrent first
     * binds on the same channel can both observe `existingForChannel.isEmpty() = true` and
     * both try `isWriter = true`. The partial unique index lets the second insert fail with
     * a UNIQUE violation, which the DAO swallows as [RelayDao.BindOutcome.AlreadyBound].
     * Distinguish that case (the (channel, group) row genuinely doesn't exist) and retry
     * the loser as a reader.
     */
    fun bind(
        guildId: Long,
        channelId: Long,
        group: String,
        showSnitches: Boolean,
        createdBy: Long,
    ): BindResult {
        val existingForChannel = dao.listForChannel(channelId)
        val isWriter = existingForChannel.isEmpty()
        return when (dao.bind(guildId, channelId, group, isWriter, showSnitches, createdBy)) {
            is RelayDao.BindOutcome.Inserted -> if (isWriter) BindResult.Writer else BindResult.Reader
            RelayDao.BindOutcome.AlreadyBound -> {
                if (isWriter && dao.findByChannelAndGroup(channelId, group) == null) {
                    when (dao.bind(guildId, channelId, group, false, showSnitches, createdBy)) {
                        is RelayDao.BindOutcome.Inserted -> BindResult.Reader
                        RelayDao.BindOutcome.AlreadyBound -> BindResult.ChannelAlreadyBound
                    }
                } else {
                    BindResult.ChannelAlreadyBound
                }
            }
        }
    }

    fun unbind(channelId: Long, group: String): UnbindResult = if (dao.unbind(channelId, group)) {
        UnbindResult.Unbound
    } else {
        UnbindResult.NotBound
    }

    fun promoteWriter(channelId: Long, group: String): PromoteWriterResult = if (dao.promoteToWriter(channelId, group)) {
        PromoteWriterResult.Promoted
    } else {
        PromoteWriterResult.NotBound
    }

    fun listForChannel(channelId: Long): List<Relay> = dao.listForChannel(channelId)

    fun findByChannelAndGroup(channelId: Long, group: String): Relay? = dao.findByChannelAndGroup(channelId, group)

    fun findWriterForChannel(channelId: Long): Relay? = dao.findWriterForChannel(channelId)

    fun listForGuild(guildId: Long): List<Relay> = dao.listForGuild(guildId)

    fun setShowSnitches(channelId: Long, group: String, value: Boolean): SetResult = if (dao.setShowSnitches(channelId, group, value) > 0) {
        SetResult.Updated
    } else {
        SetResult.NotBound
    }

    fun setChatFormat(channelId: Long, group: String, value: String?): SetResult = if (dao.setChatFormat(channelId, group, value) > 0) {
        SetResult.Updated
    } else {
        SetResult.NotBound
    }

    fun setSnitchPing(channelId: Long, group: String, value: String?): SetResult = if (dao.setSnitchPing(channelId, group, value) > 0) {
        SetResult.Updated
    } else {
        SetResult.NotBound
    }
}
