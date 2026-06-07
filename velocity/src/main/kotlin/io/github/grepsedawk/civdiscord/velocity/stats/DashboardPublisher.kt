package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.db.StatsBinding
import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import net.dv8tion.jda.api.entities.MessageEmbed
import java.time.Instant

/** Maintains the single live dashboard message: posts it once, edits it forever, reposts if it was
 *  deleted. The binding is read once per update so a concurrent /stats dashboard-clear can't split
 *  channel/message across reads. At most one post/edit is in flight at a time (inFlight) — the
 *  injected JDA calls always report a [Result], so inFlight is always released without a timer. A
 *  failure (perms, transient 5xx) backs the channel off for a cooldown rather than latching forever,
 *  so it recovers on its own. A deleted message just drops the stored id; the next tick reposts. A
 *  post is attached to the binding only if it still points at the same channel; otherwise the stray
 *  panel is deleted. JDA calls are injected. */
class DashboardPublisher(
    private val binding: () -> StatsBinding?,
    private val clearMessageId: () -> Unit,
    private val attachMessage: (channelId: Long, messageId: Long) -> Boolean,
    private val post: (channelId: Long, embed: MessageEmbed, onResult: (Result) -> Unit) -> Unit,
    private val edit: (channelId: Long, messageId: Long, embed: MessageEmbed, onResult: (Result) -> Unit) -> Unit,
    private val deleteMessage: (channelId: Long, messageId: Long) -> Unit,
    private val now: () -> Long = { Instant.now().epochSecond },
    private val retryCooldownSeconds: Long = 600,
    private val render: (StatsSnapshot) -> MessageEmbed = DashboardEmbed::render,
) {
    sealed interface Result {
        data class Posted(val messageId: Long) : Result

        object Edited : Result

        /** The target message no longer exists; drop the stored id and repost next tick. */
        object Missing : Result

        /** A failure (perms, transient 5xx); back the channel off for the cooldown. */
        object Failed : Result

        /** Could not even attempt (e.g. JDA not ready yet); retry next tick, no state change. */
        object Skipped : Result
    }

    @Volatile
    private var inFlight = false

    @Volatile
    private var inFlightSince = 0L

    @Volatile
    private var backoffChannel: Long? = null

    @Volatile
    private var backoffUntil = 0L

    fun update(snapshot: StatsSnapshot) {
        val b = binding() ?: return
        val ch = b.dashboardChannelId ?: return
        val nowEpoch = now()
        // A genuinely lost JDA callback would otherwise wedge inFlight forever; this timeout is far
        // longer than any real post/edit (Discord rate limits resolve in seconds), so it fires only
        // for a truly stuck op and never races a slow-but-alive one into a duplicate post.
        if (inFlight && nowEpoch - inFlightSince < INFLIGHT_TIMEOUT_SECONDS) return
        if (backoffChannel == ch && nowEpoch < backoffUntil) return
        val embed = render(snapshot)
        val mid = b.dashboardMessageId
        inFlight = true
        inFlightSince = nowEpoch
        if (mid == null) {
            post(ch, embed) { onResult(ch, it) }
        } else {
            edit(ch, mid, embed) { onResult(ch, it) }
        }
    }

    /** Retry immediately after an operator re-runs /stats dashboard-set, ignoring any active backoff. */
    fun retryAfterRebind() {
        clearBackoff()
    }

    private fun onResult(ch: Long, result: Result) {
        when (result) {
            is Result.Posted -> {
                clearBackoff()
                // Attach only if the binding still points here; a clear/re-channel that landed while
                // this post was in flight means the panel is now stray, so delete it.
                if (!attachMessage(ch, result.messageId)) deleteMessage(ch, result.messageId)
            }
            Result.Edited -> clearBackoff()
            Result.Missing -> clearMessageId()
            Result.Failed -> {
                backoffChannel = ch
                backoffUntil = now() + retryCooldownSeconds
            }
            Result.Skipped -> Unit
        }
        // Cleared last so a stats-thread update() that observes inFlight==false also sees the saved id.
        inFlight = false
    }

    private fun clearBackoff() {
        backoffChannel = null
        backoffUntil = 0
    }

    private companion object {
        const val INFLIGHT_TIMEOUT_SECONDS = 300L
    }
}
