package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.PatreonTierDao
import io.github.grepsedawk.civdiscord.velocity.auth.RoleGranter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executor

class MeCommandTest {
    private data class Fx(
        val cmd: MeCommand,
        val bindings: BindingDao,
        val tiers: PatreonTierDao,
        val guildsDao: GuildDao,
        val syncCalls: MutableList<Long>,
    )

    private fun fixture(
        registeredGuilds: List<Long> = emptyList(),
    ): Fx {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        val tiers = PatreonTierDao(db)
        val guildsDao = GuildDao(db)
        for (gid in registeredGuilds) guildsDao.ensure(gid)
        val syncCalls = mutableListOf<Long>()
        val granter =
            RoleGranter(
                guilds = guildsDao,
                isMemberOf = { _, _ -> true },
                grant = { _, _, _ -> syncCalls.add(0L) },
            )
        val executor = Executor { r ->
            syncCalls.add(1L)
            r.run()
        }
        val cmd = MeCommand(bindings, tiers, guildsDao, granter, executor)
        return Fx(cmd, bindings, tiers, guildsDao, syncCalls)
    }

    private fun replyEvent(
        userId: Long,
        jda: JDA = mockk<JDA>(relaxed = true),
    ): SlashCommandInteractionEvent {
        val event = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { event.user.idLong } returns userId
        every { event.jda } returns jda
        val replyAction = mockk<ReplyCallbackAction>(relaxed = true)
        every { event.reply(any<String>()) } returns replyAction
        every { replyAction.setEphemeral(any()) } returns replyAction
        return event
    }

    @Test
    fun `unlinked user gets a guidance message`() {
        val (cmd, _, _, _, syncCalls) = fixture()
        val event = replyEvent(42L)
        cmd.handle(event)
        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldContain("/discord link")
        syncCalls.shouldBe(emptyList())
    }

    @Test
    fun `linked user with tier shows binding and tier`() {
        val (cmd, bindings, tiers, _, _) = fixture()
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        tiers.set(42L, "gold")
        val event = replyEvent(42L)
        cmd.handle(event)
        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldContain("alice")
        msg.captured.shouldContain("gold")
    }

    @Test
    fun `linked user with a backtick in the mc name does not break markdown`() {
        val (cmd, bindings, _, _, _) = fixture()
        bindings.upsert(42L, UUID.randomUUID(), "weird`name")
        val event = replyEvent(42L)
        cmd.handle(event)
        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldNotContain("weird`name")
    }

    @Test
    fun `linked user with a guild membership lists per-guild Discord roles`() {
        val (cmd, bindings, _, _, _) = fixture(registeredGuilds = listOf(100L))
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        val jda = mockk<JDA>(relaxed = true)
        val guild = mockk<Guild>(relaxed = true)
        val member = mockk<Member>(relaxed = true)
        val role = mockk<Role>(relaxed = true)
        every { role.name } returns "Verified"
        every { member.roles } returns listOf(role)
        every { guild.name } returns "Townhall"
        every { guild.getMemberById(42L) } returns member
        every { jda.getGuildById(100L) } returns guild
        val event = replyEvent(42L, jda)
        cmd.handle(event)
        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldContain("Discord roles")
        msg.captured.shouldContain("Townhall")
        msg.captured.shouldContain("Verified")
    }

    @Test
    fun `linked user not present in any registered guild omits the per-guild section`() {
        val (cmd, bindings, _, _, _) = fixture(registeredGuilds = listOf(100L))
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        val jda = mockk<JDA>(relaxed = true)
        val guild = mockk<Guild>(relaxed = true)
        every { guild.getMemberById(42L) } returns null
        every { jda.getGuildById(100L) } returns guild
        val event = replyEvent(42L, jda)
        cmd.handle(event)
        val msg = slot<String>()
        verify { event.reply(capture(msg)) }
        msg.captured.shouldNotContain("Discord roles")
    }

    @Test
    fun `linked user triggers role re-sync via syncExecutor`() {
        val (cmd, bindings, _, _, syncCalls) = fixture()
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        val event = replyEvent(42L)
        cmd.handle(event)
        (syncCalls.contains(1L)) shouldBe true
    }
}
