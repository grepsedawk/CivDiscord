package io.github.grepsedawk.civdiscord.velocity.auth

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import org.junit.jupiter.api.Test
import java.util.UUID

class MemberAddListenerTest {
    @Test
    fun `joining user that is already linked triggers grantForGuild`() {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        var grantedFor: Pair<Long, Long>? = null
        val listener =
            MemberAddListener(
                bindings = bindings,
                grant = { guildId, discordId -> grantedFor = guildId to discordId },
            )
        val event = mockk<GuildMemberJoinEvent>(relaxed = true)
        every { event.user.idLong } returns 42L
        every { event.guild.idLong } returns 100L
        listener.onGuildMemberJoin(event)
        grantedFor shouldBe (100L to 42L)
    }

    @Test
    fun `joining user that is not linked is a no-op even when other bindings exist`() {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        bindings.upsert(99L, UUID.randomUUID(), "bob") // someone else is linked
        var granted = false
        val listener = MemberAddListener(bindings) { _, _ -> granted = true }
        val event = mockk<GuildMemberJoinEvent>(relaxed = true)
        every { event.user.idLong } returns 42L
        every { event.guild.idLong } returns 100L
        listener.onGuildMemberJoin(event)
        granted shouldBe false
    }

    @Test
    fun `dao exception is swallowed and listener continues`() {
        val bindings = mockk<BindingDao>()
        every { bindings.findByDiscordId(any()) } throws RuntimeException("disk full")
        var grantCalled = false
        val listener = MemberAddListener(bindings, grant = { _, _ -> grantCalled = true })
        val event = mockk<GuildMemberJoinEvent>(relaxed = true)
        every { event.user.idLong } returns 42L
        every { event.guild.idLong } returns 100L
        listener.onGuildMemberJoin(event) // must NOT throw
        grantCalled shouldBe false
    }
}
