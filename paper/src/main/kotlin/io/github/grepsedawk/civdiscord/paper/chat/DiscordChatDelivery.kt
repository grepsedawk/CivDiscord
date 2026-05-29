package io.github.grepsedawk.civdiscord.paper.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.bukkit.ChatColor
import java.util.UUID

/**
 * Renders a Discord-sourced chat message and delivers it to every online member of the
 * target NameLayer group. Strip §-codes from user-controlled fields so a Discord user
 * can't paste `§4admin§r` to spoof staff coloring.
 *
 * [memberLookup] returns the UUIDs of the group's roster, or null if the group is unknown
 * to this backend (Paper drops the payload — likely another backend owns the group).
 * [sendTo] is invoked once per online member with the rendered line.
 */
class DiscordChatDelivery(
    private val memberLookup: (group: String) -> List<UUID>?,
    private val sendTo: (uuid: UUID, message: String) -> Unit,
) {
    fun handle(msg: Payload.ChatToMc) {
        val members = memberLookup(msg.namelayerGroup) ?: return
        val rendered = render(msg)
        for (uuid in members) sendTo(uuid, rendered)
    }

    private fun render(msg: Payload.ChatToMc): String {
        val from = ChatColor.stripColor(msg.from).orEmpty()
        val text = ChatColor.stripColor(msg.text).orEmpty()
        return "${ChatColor.BLUE}${ChatColor.BOLD}[Discord]${ChatColor.RESET} " +
            "${ChatColor.WHITE}<$from>${ChatColor.GRAY} $text"
    }
}
