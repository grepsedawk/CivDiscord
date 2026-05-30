package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Test
import java.util.EnumSet

class AdminGuildCommandTest {
    private fun fixture(): Pair<AdminGuildCommand, GuildDao> {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(100L)
        return AdminGuildCommand(dao) to dao
    }

    private fun replyEvent(): Pair<SlashCommandInteractionEvent, ReplyCallbackAction> {
        val e = mockk<SlashCommandInteractionEvent>(relaxed = true)
        val r = mockk<ReplyCallbackAction>(relaxed = true)
        every { e.reply(any<String>()) } returns r
        every { r.setEphemeral(any()) } returns r
        val user = mockk<User>(relaxed = true)
        every { user.idLong } returns 7L
        every { e.user } returns user
        return e to r
    }

    private fun benignRole(id: Long = 999L, name: String = "Member"): Role {
        val role = mockk<Role>(relaxed = true)
        every { role.idLong } returns id
        every { role.name } returns name
        every { role.isManaged } returns false
        every { role.isPublicRole } returns false
        every { role.permissions } returns EnumSet.noneOf(Permission::class.java)
        return role
    }

    private fun bindGuildSelf(e: SlashCommandInteractionEvent, role: Role, canInteract: Boolean = true) {
        val guild = mockk<Guild>(relaxed = true)
        every { e.guild } returns guild
        every { guild.idLong } returns 100L
        val self = mockk<Member>(relaxed = true)
        every { guild.selfMember } returns self
        every { self.canInteract(role) } returns canInteract
    }

    @Test
    fun `auth-role sets the role id`() {
        val (cmd, dao) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "auth-role"
        val role = benignRole()
        bindGuildSelf(e, role)
        val opt = mockk<OptionMapping>()
        every { opt.asRole } returns role
        every { e.getOption("role") } returns opt
        cmd.handle(e)
        dao.find(100L)!!.authRoleId shouldBe 999L
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("<@&999>")
    }

    @Test
    fun `auth-role outside a guild (DM) rejects`() {
        val (cmd, _) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "auth-role"
        every { e.guild } returns null
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("must be used in a guild")
    }

    @Test
    fun `missing role option replies Missing role`() {
        val (cmd, _) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "auth-role"
        every { e.guild?.idLong } returns 100L
        every { e.getOption("role") } returns null
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured shouldBe "Missing role."
    }

    @Test
    fun `auth-role with role from another guild rejects`() {
        val (cmd, _) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "auth-role"
        every { e.guild?.idLong } returns 100L
        val opt = mockk<OptionMapping>()
        every { opt.asRole } throws IllegalStateException("Could not resolve Role")
        every { e.getOption("role") } returns opt
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured shouldBe "Unknown role."
    }

    @Test
    fun `unknown subcommand replies with the unknown error`() {
        val (cmd, _) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "wat"
        every { e.guild?.idLong } returns 100L
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("Unknown subcommand")
        msg.captured.shouldContain("wat")
    }

    @Test
    fun `view returns the current config`() {
        val (cmd, dao) = fixture()
        dao.setAuthRole(100L, 999L)
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "view"
        every { e.guild?.idLong } returns 100L
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("<@&999>")
    }

    @Test
    fun `view with no auth role set reports unset`() {
        val (cmd, _) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "view"
        every { e.guild?.idLong } returns 100L
        cmd.handle(e)
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("(unset)")
    }

    @Test
    fun `auth-role rejects @everyone`() {
        val (cmd, dao) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "auth-role"
        val role = benignRole(id = 100L)
        every { role.isPublicRole } returns true
        bindGuildSelf(e, role)
        val opt = mockk<OptionMapping>()
        every { opt.asRole } returns role
        every { e.getOption("role") } returns opt
        cmd.handle(e)
        dao.find(100L)!!.authRoleId shouldBe null
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("@everyone")
    }

    @Test
    fun `auth-role rejects managed roles`() {
        val (cmd, dao) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "auth-role"
        val role = benignRole(name = "Server Booster")
        every { role.isManaged } returns true
        bindGuildSelf(e, role)
        val opt = mockk<OptionMapping>()
        every { opt.asRole } returns role
        every { e.getOption("role") } returns opt
        cmd.handle(e)
        dao.find(100L)!!.authRoleId shouldBe null
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("managed")
    }

    @Test
    fun `auth-role rejects roles above bot`() {
        val (cmd, dao) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "auth-role"
        val role = benignRole(name = "Higher")
        bindGuildSelf(e, role, canInteract = false)
        val opt = mockk<OptionMapping>()
        every { opt.asRole } returns role
        every { e.getOption("role") } returns opt
        cmd.handle(e)
        dao.find(100L)!!.authRoleId shouldBe null
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain("above the bot")
    }

    @Test
    fun `auth-role rejects ADMINISTRATOR`() {
        assertDangerousRejected("Admins", Permission.ADMINISTRATOR, "ADMINISTRATOR")
    }

    @Test
    fun `auth-role rejects MANAGE_SERVER`() {
        assertDangerousRejected("Mods", Permission.MANAGE_SERVER, "MANAGE_SERVER")
    }

    @Test
    fun `auth-role rejects MANAGE_ROLES`() {
        assertDangerousRejected("RoleMods", Permission.MANAGE_ROLES, "MANAGE_ROLES")
    }

    @Test
    fun `auth-role rejects MANAGE_CHANNEL`() {
        assertDangerousRejected("ChanMods", Permission.MANAGE_CHANNEL, "MANAGE_CHANNEL")
    }

    @Test
    fun `auth-role rejects BAN_MEMBERS`() {
        assertDangerousRejected("Banners", Permission.BAN_MEMBERS, "BAN_MEMBERS")
    }

    @Test
    fun `auth-role rejects KICK_MEMBERS`() {
        assertDangerousRejected("Kickers", Permission.KICK_MEMBERS, "KICK_MEMBERS")
    }

    private fun assertDangerousRejected(roleName: String, perm: Permission, permName: String) {
        val (cmd, dao) = fixture()
        val (e, _) = replyEvent()
        every { e.subcommandName } returns "auth-role"
        val role = benignRole(name = roleName)
        every { role.permissions } returns EnumSet.of(perm)
        bindGuildSelf(e, role)
        val opt = mockk<OptionMapping>()
        every { opt.asRole } returns role
        every { e.getOption("role") } returns opt
        cmd.handle(e)
        dao.find(100L)!!.authRoleId shouldBe null
        val msg = slot<String>()
        verify { e.reply(capture(msg)) }
        msg.captured.shouldContain(permName)
        msg.captured.shouldContain(roleName)
    }
}
