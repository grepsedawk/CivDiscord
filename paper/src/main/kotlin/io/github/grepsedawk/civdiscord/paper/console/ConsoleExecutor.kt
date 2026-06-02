package io.github.grepsedawk.civdiscord.paper.console

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.bukkit.Server
import org.bukkit.command.ConsoleCommandSender

class ConsoleExecutor(
    private val server: Server,
    private val serverName: String,
) {

    fun run(req: Payload.ConsoleRequest): Payload.ConsoleReply {
        if (req.server != serverName && req.server != "*") {
            return Payload.ConsoleReply(req.id, false, "wrong server")
        }
        return try {
            val buffer = StringBuilder()
            val capturingSender = CapturingSender(server.consoleSender, buffer)
            val found = server.dispatchCommand(capturingSender, req.command)
            val rendered = buildString {
                append(buffer.toString().trim())
                if (capturingSender.truncated) {
                    if (isNotEmpty()) append('\n')
                    append("...(truncated)")
                }
            }
            if (found) {
                Payload.ConsoleReply(req.id, true, rendered.ifEmpty { "(no output)" })
            } else {
                Payload.ConsoleReply(req.id, false, "unknown command: ${req.command}")
            }
        } catch (e: Exception) {
            Payload.ConsoleReply(req.id, false, "error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

/**
 * Wraps Paper's console sender, also accumulating every sendMessage call into a buffer so
 * we can ship the output back to Discord via the bridge ConsoleReply payload.
 *
 * Caps appended bytes at [maxBytes] — once over, drops the rest and flips [truncated].
 */
private class CapturingSender(
    private val delegate: ConsoleCommandSender,
    private val buffer: StringBuilder,
    private val maxBytes: Int = 64 * 1024,
) : ConsoleCommandSender by delegate {
    var truncated: Boolean = false
        private set

    override fun sendMessage(message: String) {
        if (buffer.length + message.length <= maxBytes) {
            buffer.appendLine(message)
        } else {
            truncated = true
        }
        delegate.sendMessage(message)
    }

    override fun sendMessage(vararg messages: String) {
        for (m in messages) sendMessage(m)
    }
}
