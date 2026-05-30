package io.github.grepsedawk.civdiscord.paper.linker

import io.kotest.matchers.shouldBe
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Test

class LinkMessageTest {

    @Test
    fun `link message has copy-to-clipboard click event for the token`() {
        val token = "ABC123XYZ"
        val msg = buildLinkMessage(token)
        val found = msg.children().any { child ->
            child.clickEvent()?.action() == ClickEvent.Action.COPY_TO_CLIPBOARD &&
                child.clickEvent()?.value() == token
        }
        found shouldBe true
    }
}
