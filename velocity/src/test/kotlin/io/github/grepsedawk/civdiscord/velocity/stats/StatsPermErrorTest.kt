package io.github.grepsedawk.civdiscord.velocity.stats

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import net.dv8tion.jda.api.Permission
import org.junit.jupiter.api.Test
import java.awt.Color

class StatsPermErrorTest {
    @Test
    fun `red embed names the channel, the missing perms, and that nothing changed`() {
        val e = StatsPermError.embed(123L, listOf(Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS))
        e.color shouldBe Color(0xE74C3C)
        val desc = e.description!!
        desc shouldContain "<#123>"
        desc shouldContain "Send Messages"
        desc shouldContain "Embed Links"
        desc shouldContain "didn't bind anything"
    }
}
