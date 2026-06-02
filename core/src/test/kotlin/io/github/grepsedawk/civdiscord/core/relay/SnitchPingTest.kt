package io.github.grepsedawk.civdiscord.core.relay

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class SnitchPingTest {

    @Test
    fun `parses role mention`() {
        val p = SnitchPing.parse("<@&123>")
        p.shouldBeInstanceOf<SnitchPing.Role>()
        p.id shouldBe 123L
        p.mention shouldBe "<@&123>"
    }

    @Test
    fun `parses user mention`() {
        val p = SnitchPing.parse("<@456>")
        p.shouldBeInstanceOf<SnitchPing.User>()
        p.id shouldBe 456L
        p.mention shouldBe "<@456>"
    }

    @Test
    fun `normalizes legacy nickname mention to canonical user mention`() {
        val p = SnitchPing.parse("<@!789>")
        p.shouldBeInstanceOf<SnitchPing.User>()
        p.id shouldBe 789L
        p.mention shouldBe "<@789>"
    }

    @Test
    fun `null input returns null`() {
        SnitchPing.parse(null).shouldBeNull()
    }

    @Test
    fun `blank input returns null`() {
        SnitchPing.parse("").shouldBeNull()
        SnitchPing.parse("   ").shouldBeNull()
    }

    @Test
    fun `garbage input returns null`() {
        SnitchPing.parse("@Defenders").shouldBeNull()
        SnitchPing.parse("<@&abc>").shouldBeNull()
        SnitchPing.parse("<@>").shouldBeNull()
        SnitchPing.parse("hello").shouldBeNull()
    }

    @Test
    fun `trims surrounding whitespace`() {
        (SnitchPing.parse(" <@&123> ") as SnitchPing.Role).id shouldBe 123L
        (SnitchPing.parse("\t<@456>\n") as SnitchPing.User).id shouldBe 456L
    }

    @Test
    fun `overflow id returns null instead of throwing`() {
        SnitchPing.parse("<@&99999999999999999999>").shouldBeNull()
        SnitchPing.parse("<@99999999999999999999>").shouldBeNull()
    }

    @Test
    fun `round-trip of mention is stable`() {
        val role = SnitchPing.parse("<@&123>")!!
        SnitchPing.parse(role.mention)!!.mention shouldBe "<@&123>"
        val user = SnitchPing.parse("<@!789>")!!
        SnitchPing.parse(user.mention)!!.mention shouldBe "<@789>"
    }

    @Test
    fun `parses literal at-everyone as Everyone`() {
        val p = SnitchPing.parse("@everyone")
        p.shouldBeInstanceOf<SnitchPing.Everyone>()
        p.mention shouldBe "@everyone"
    }

    @Test
    fun `round-trip of Everyone mention is stable`() {
        val p = SnitchPing.parse("@everyone")!!
        SnitchPing.parse(p.mention)!!.mention shouldBe "@everyone"
    }

    @Test
    fun `trims surrounding whitespace around at-everyone`() {
        SnitchPing.parse(" @everyone ").shouldBeInstanceOf<SnitchPing.Everyone>()
    }
}
