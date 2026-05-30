package io.github.grepsedawk.civdiscord.paper.linker

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

class DiscordCommand(
    private val send: (Payload) -> Unit,
    private val pending: PendingLinkReplies,
    private val pendingStatus: PendingStatusReplies = PendingStatusReplies(),
) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean = onCommand(sender, label, args)

    fun onCommand(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("/discord can only be run by a player.")
            return true
        }
        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> sendHelp(sender)
            "link" -> startLink(sender)
            "status" -> showStatus(sender)
            else -> sender.sendMessage("Unknown subcommand: ${args[0]}. Try /discord help.")
        }
        return true
    }

    private fun sendHelp(p: Player) {
        p.sendMessage("/discord link – start linking your Discord account")
        p.sendMessage("/discord status – show your current Discord link")
    }

    private fun startLink(p: Player) {
        val id = UUID.randomUUID().toString()
        pending.remember(id, p.uniqueId)
        send(Payload.LinkRequest(id = id, mcUuid = p.uniqueId.toString(), mcName = p.name))
        p.sendMessage("Requested a link code…")
    }

    private fun showStatus(p: Player) {
        val id = UUID.randomUUID().toString()
        pendingStatus.remember(id, p.uniqueId)
        send(Payload.StatusRequest(id = id, mcUuid = p.uniqueId.toString()))
        p.sendMessage("§7Looking up your Discord link…")
    }
}
