package io.github.grepsedawk.civdiscord.velocity.discord

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class SkinUrlTest {

    @Test
    fun `head returns mc-heads url for dashed uuid`() {
        val uuid = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")
        SkinUrl.head(uuid) shouldBe "https://mc-heads.net/head/0111b95d-110c-4ea1-b4b2-59afeff296f4/128"
    }

    @Test
    fun `head accepts uuid by string`() {
        SkinUrl.head("0111b95d-110c-4ea1-b4b2-59afeff296f4") shouldBe
            "https://mc-heads.net/head/0111b95d-110c-4ea1-b4b2-59afeff296f4/128"
    }
}
