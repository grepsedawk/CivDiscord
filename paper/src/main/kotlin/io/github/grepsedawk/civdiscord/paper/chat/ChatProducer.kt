package io.github.grepsedawk.civdiscord.paper.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

/**
 * Forwards MC chat to Discord via the bridge, scoped to the player's currently-active
 * NameLayer chat group (via [groupFor]). Players with no active group are skipped — global
 * chat is intentionally NOT relayed.
 *
 * AsyncPlayerChatEvent fires off-thread; [emit] is the lambda that performs the actual
 * bridge send, and the entrypoint is responsible for hopping to main (via Bukkit scheduler)
 * inside it if needed.
 */
class ChatProducer(
    private val serverName: String,
    private val groupFor: (Player) -> String?,
    private val emit: (Payload.ChatToDiscord) -> Unit,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun on(event: AsyncPlayerChatEvent) {
        val player = event.player
        val group = groupFor(player) ?: return
        emit(
            Payload.ChatToDiscord(
                server = serverName,
                fromUuid = player.uniqueId.toString(),
                fromName = player.name,
                namelayerGroup = group,
                text = event.message,
            ),
        )
    }
}
