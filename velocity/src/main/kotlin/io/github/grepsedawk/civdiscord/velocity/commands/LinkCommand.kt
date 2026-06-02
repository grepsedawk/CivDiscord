package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.auth.LinkAttemptLimiter
import io.github.grepsedawk.civdiscord.core.auth.LinkService
import io.github.grepsedawk.civdiscord.core.text.MarkdownSafe
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.LoggerFactory
import java.util.UUID

class LinkCommand(
    private val service: LinkService,
    private val limiter: LinkAttemptLimiter,
    private val onLinked: (discordId: Long, mcUuid: UUID, replaced: Boolean) -> Unit,
) {
    private val log = LoggerFactory.getLogger(LinkCommand::class.java)

    fun handle(event: SlashCommandInteractionEvent) {
        val discordId = event.user.idLong
        if (limiter.isLockedOut(discordId)) {
            log.info("link refused (rate-limited): discord={}", discordId)
            event.reply("Too many failed attempts. Wait 10 minutes.").setEphemeral(true).queue()
            return
        }
        val code = event.getOption("code")?.asString ?: run {
            event.reply("Missing code.").setEphemeral(true).queue()
            return
        }
        when (val r = service.redeem(discordId, code)) {
            is LinkService.Result.Linked -> {
                limiter.recordSuccess(discordId)
                log.info("link succeeded: discord={} mc={} replaced={}", discordId, r.mcUuid, r.replaced)
                event.reply("Linked to MC account `${MarkdownSafe.code(r.mcName)}`.").setEphemeral(true).queue()
                onLinked(discordId, r.mcUuid, r.replaced)
            }
            is LinkService.Result.McAlreadyLinked -> {
                limiter.recordFailure(discordId)
                log.info(
                    "link refused (mc already linked): discord={} otherDiscord={}",
                    discordId,
                    r.otherDiscordId,
                )
                event.reply(
                    "That Minecraft account is already linked to <@${r.otherDiscordId}>. " +
                        "Ask an admin to /admin user unlink first.",
                ).setEphemeral(true).queue()
            }
            LinkService.Result.NoSuchCode -> {
                limiter.recordFailure(discordId)
                log.info("link attempt failed: discord={} code=*****", discordId)
                event.reply("No such code or expired.").setEphemeral(true).queue()
            }
        }
    }
}
