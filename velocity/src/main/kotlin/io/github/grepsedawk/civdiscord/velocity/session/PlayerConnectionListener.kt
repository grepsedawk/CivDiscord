package io.github.grepsedawk.civdiscord.velocity.session

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import java.time.Instant
import java.util.concurrent.Executor

/**
 * Translates proxy connect/disconnect events into login/logout/switch lines and posts them to the
 * bound feed channel. The tracker is updated synchronously on the event thread (ordering matters);
 * the channel lookup, formatting, and Discord send run on [worker] off the event thread.
 */
class PlayerConnectionListener(
    private val tracker: ConnectionTracker,
    private val feedChannel: () -> Long?,
    private val send: (channelId: Long, text: String) -> Unit,
    private val worker: Executor,
    private val now: () -> Long = { Instant.now().epochSecond },
) {
    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val transition = tracker.connected(event.player.uniqueId, event.server.serverInfo.name)
        emit(event.player.username, transition)
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val transition = tracker.disconnected(event.player.uniqueId)
        emit(event.player.username, transition)
    }

    private fun emit(name: String, transition: Transition) {
        if (transition is Transition.None) return
        val epoch = now()
        worker.execute {
            val channelId = feedChannel() ?: return@execute
            val text = LoginLogoutLine.render(name, transition, epoch) ?: return@execute
            send(channelId, text)
        }
    }
}
