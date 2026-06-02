package io.github.grepsedawk.civdiscord.velocity.discord

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class SkinUrlTest {

    @Test
    fun `avatar returns mc-heads avatar url for dashed uuid`() {
        val uuid = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")
        SkinUrl.avatar(uuid) shouldBe "https://mc-heads.net/avatar/0111b95d-110c-4ea1-b4b2-59afeff296f4/128"
    }

    @Test
    fun `avatar accepts uuid by string`() {
        SkinUrl.avatar("0111b95d-110c-4ea1-b4b2-59afeff296f4") shouldBe
            "https://mc-heads.net/avatar/0111b95d-110c-4ea1-b4b2-59afeff296f4/128"
    }
}
