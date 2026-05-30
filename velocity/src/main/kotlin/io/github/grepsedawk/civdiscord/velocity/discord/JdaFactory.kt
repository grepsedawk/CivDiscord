package io.github.grepsedawk.civdiscord.velocity.discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.messages.MessageRequest

object JdaFactory {
    fun build(
        token: String,
        listeners: List<Any>,
    ): JDA {
        MessageRequest.setDefaultMentions(emptyList<Message.MentionType>())
        return JDABuilder.createDefault(token)
            .enableIntents(
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
            )
            .setMemberCachePolicy(MemberCachePolicy.OWNER)
            .setChunkingFilter(ChunkingFilter.NONE)
            .addEventListeners(*listeners.toTypedArray())
            .build()
    }
}
