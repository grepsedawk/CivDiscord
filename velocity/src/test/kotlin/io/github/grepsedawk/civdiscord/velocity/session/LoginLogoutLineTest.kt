package io.github.grepsedawk.civdiscord.velocity.session

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LoginLogoutLineTest {
    private val epoch = 1718000000L

    @Test
    fun `login line uses a dynamic timestamp and the server bracket`() {
        LoginLogoutLine.render("x1025", Transition.Login("eden"), epoch) shouldBe
            "<t:1718000000:T> `[eden]` x1025 logged in"
    }

    @Test
    fun `logout line uses the last server`() {
        LoginLogoutLine.render("x1025", Transition.Logout("eden"), epoch) shouldBe
            "<t:1718000000:T> `[eden]` x1025 logged out"
    }

    @Test
    fun `switch line names both servers`() {
        LoginLogoutLine.render("x1025", Transition.Switch("eden", "minigames"), epoch) shouldBe
            "<t:1718000000:T> x1025 moved from `eden` to `minigames`"
    }

    @Test
    fun `none renders nothing`() {
        LoginLogoutLine.render("x1025", Transition.None, epoch).shouldBeNull()
    }
}
