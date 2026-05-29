package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.function.Consumer

class AdminRunCommand(
    private val backends: () -> List<String>,
    private val dispatch: (server: String, payload: Payload) -> Unit,
    private val registerPending: (id: String, hook: InteractionHook) -> Unit,
    private val unregisterPending: (id: String) -> Unit = {},
) {
    private val log = LoggerFactory.getLogger(AdminRunCommand::class.java)

    fun handle(event: SlashCommandInteractionEvent) {
        val server = event.getOption("server")?.asString ?: return replyEphemeral(event, "Missing server.")
        val command = event.getOption("command")?.asString ?: return replyEphemeral(event, "Missing command.")
        val known = backends()
        if (server !in known) {
            replyEphemeral(event, "Unknown server `$server`. Known: ${known.joinToString(", ") { "`$it`" }}")
            return
        }
        log.info("admin run by actor={} server={} command={}", event.user.idLong, server, command)
        val id = UUID.randomUUID().toString()
        event.deferReply(true).queue(
            Consumer { hook ->
                registerPending(id, hook)
                try {
                    dispatch(server, Payload.ConsoleRequest(id, server, command))
                } catch (t: Throwable) {
                    unregisterPending(id)
                    hook.editOriginal(
                        "Failed to dispatch console request: ${t.message ?: t.javaClass.simpleName}",
                    ).queue(null, Consumer {})
                }
            },
            Consumer {},
        )
    }

    private fun replyEphemeral(
        e: SlashCommandInteractionEvent,
        msg: String,
    ) {
        e.reply(msg).setEphemeral(true).queue()
    }
}
