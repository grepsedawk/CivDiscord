package io.github.grepsedawk.civdiscord.paper.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import vg.civcraft.mc.civchat2.event.GroupChatEvent

/**
 * Hooks CivChat2's GroupChatEvent — fires ONLY for messages CivChat2 routed through its
 * group-chat channel (`/g <group> msg` or normal chat when the player's active channel is
 * a group, including a `!` group used as "global"). GlobalChatEvent and PrivateMessageEvent
 * are intentionally NOT relayed.
 */
class GroupChatProducer(
    private val serverName: String,
    private val emit: (Payload.ChatToDiscord) -> Unit,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun on(event: GroupChatEvent) {
        // CivChat2's GroupChatEvent#getGroup() returns the group NAME as String, not a Group object.
        emit(
            Payload.ChatToDiscord(
                server = serverName,
                fromUuid = event.player.uniqueId.toString(),
                fromName = event.player.name,
                namelayerGroup = event.group,
                text = event.message,
            ),
        )
    }
}
