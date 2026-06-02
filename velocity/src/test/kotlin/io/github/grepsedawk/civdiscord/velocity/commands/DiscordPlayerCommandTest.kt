package io.github.grepsedawk.civdiscord.velocity.commands

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.Player
import io.github.grepsedawk.civdiscord.core.auth.LinkTokenStore
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Test
import java.util.UUID

class DiscordPlayerCommandTest {

    private data class Fixture(
        val cmd: DiscordPlayerCommand,
        val tokens: LinkTokenStore,
        val bindings: BindingDao,
    )

    private fun fixture(
        bindingDao: BindingDao? = null,
    ): Fixture {
        val db = CivDiscordDb.inMemory()
        val bindings = bindingDao ?: BindingDao(db)
        val tokens = LinkTokenStore()
        val cmd = DiscordPlayerCommand(tokens, bindings, org.slf4j.LoggerFactory.getLogger("test"))
        return Fixture(cmd, tokens, bindings)
    }

    private fun mockPlayer(name: String = "alice", uuid: UUID = UUID.randomUUID()): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.username } returns name
        every { p.uniqueId } returns uuid
        return p
    }

    @Test
    fun `link mints a token and sends a click-to-copy component with the code`() {
        val f = fixture()
        val cmd = f.cmd
        val tokens = f.tokens
        val uuid = UUID.randomUUID()
        val player = mockPlayer(name = "alice", uuid = uuid)

        cmd.handle(player, arrayOf("link"))

        val sent = slot<Component>()
        verify { player.sendMessage(capture(sent)) }
        val plain = sent.captured.toString()
        plain.shouldContain("Discord link code")
        plain.shouldContain("paste /link <code> in Discord")
        val codeNode = findClickCopyText(sent.captured)
        codeNode!!.length shouldBe 12
        val token = tokens.consume(codeNode)
        token!!.mcUuid shouldBe uuid
        token.mcName shouldBe "alice"
    }

    @Test
    fun `link works without any bridge call — token is minted locally on velocity`() {
        val cmd = fixture().cmd
        val player = mockPlayer()
        cmd.handle(player, arrayOf("link"))
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun `link refuses to mint when the player is already linked`() {
        val f = fixture()
        val mcUuid = UUID.randomUUID()
        f.bindings.upsert(987654321L, mcUuid, "alice")
        val player = mockPlayer(name = "alice", uuid = mcUuid)

        f.cmd.handle(player, arrayOf("link"))

        val sent = slot<Component>()
        verify { player.sendMessage(capture(sent)) }
        val plain = sent.captured.toString()
        plain.shouldContain("Already linked")
        plain.shouldContain("987654321")
        // No token was minted.
        val click = findClickCopyText(sent.captured)
        click shouldBe null
    }

    @Test
    fun `status with no binding tells the player to run discord link`() {
        val cmd = fixture().cmd
        val player = mockPlayer()
        cmd.handle(player, arrayOf("status"))
        verify {
            player.sendMessage(
                match<Component> { it.toString().contains("No Discord account linked") },
            )
        }
    }

    @Test
    fun `status with a known binding reports the discord user and mc name`() {
        val f = fixture()
        val mcUuid = UUID.randomUUID()
        f.bindings.upsert(123456789L, mcUuid, "alice")
        val player = mockPlayer(name = "alice", uuid = mcUuid)
        f.cmd.handle(player, arrayOf("status"))
        verify {
            player.sendMessage(
                match<Component> {
                    val s = it.toString()
                    s.contains("123456789") && s.contains("alice")
                },
            )
        }
    }

    @Test
    fun `no args prints help`() {
        val cmd = fixture().cmd
        val player = mockPlayer()
        cmd.handle(player, emptyArray())
        verify(atLeast = 1) { player.sendMessage(any<Component>()) }
    }

    @Test
    fun `unknown subcommand replies with a help hint`() {
        val cmd = fixture().cmd
        val player = mockPlayer()
        cmd.handle(player, arrayOf("frobnicate"))
        verify {
            player.sendMessage(
                match<Component> { it.toString().contains("Unknown subcommand") },
            )
        }
    }

    @Test
    fun `non-player console source is rejected with a clear message`() {
        val cmd = fixture().cmd
        val console = mockk<ConsoleCommandSource>(relaxed = true)
        cmd.handle(console, arrayOf("link"))
        verify {
            (console as CommandSource).sendMessage(
                match<Component> { it.toString().contains("only be run by a player") },
            )
        }
    }

    private fun findClickCopyText(c: Component): String? {
        val click = c.clickEvent()
        if (click != null && click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
            return click.value()
        }
        for (child in c.children()) {
            findClickCopyText(child)?.let { return it }
        }
        return null
    }
}
