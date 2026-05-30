package io.github.grepsedawk.civdiscord.velocity.config

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class ConfigRedactionTest {

    @Test
    fun `DiscordConfig toString does not leak token`() {
        val cfg = DiscordConfig(token = "super-secret-token-value-12345", homeGuildId = 100L)
        cfg.toString() shouldNotContain "super-secret-token-value-12345"
        cfg.toString() shouldContain "REDACTED"
        cfg.toString() shouldContain "homeGuildId=100"
    }

    @Test
    fun `PatreonConfig toString does not leak accessToken`() {
        val cfg = PatreonConfig(
            accessToken = "patreon-secret-access-token-abcdef",
            campaignId = "campaign-1",
            tierRoles = mapOf("gold" to 11L),
            syncIntervalMinutes = 60,
        )
        cfg.toString() shouldNotContain "patreon-secret-access-token-abcdef"
        cfg.toString() shouldContain "REDACTED"
        cfg.toString() shouldContain "campaign-1"
    }

    @Test
    fun `PatreonConfig toString redacts refreshToken and OAuth client creds`() {
        val cfg = PatreonConfig(
            accessToken = "at-secret",
            campaignId = "campaign-1",
            tierRoles = emptyMap(),
            syncIntervalMinutes = 60,
            refreshToken = "rt-supersecret-abcdef",
            clientId = "client-id-supersecret",
            clientSecret = "client-secret-supersecret",
        )
        val s = cfg.toString()
        s shouldNotContain "rt-supersecret-abcdef"
        s shouldNotContain "client-id-supersecret"
        s shouldNotContain "client-secret-supersecret"
        s shouldContain "refreshToken=[REDACTED]"
        s shouldContain "clientId=[REDACTED]"
        s shouldContain "clientSecret=[REDACTED]"
    }

    @Test
    fun `PatreonConfig toString shows null when OAuth fields absent`() {
        val cfg = PatreonConfig(
            accessToken = "at-secret",
            campaignId = "campaign-1",
            tierRoles = emptyMap(),
            syncIntervalMinutes = 60,
        )
        val s = cfg.toString()
        s shouldContain "refreshToken=null"
        s shouldContain "clientId=null"
        s shouldContain "clientSecret=null"
    }

    @Test
    fun `Config toString does not leak nested secrets`() {
        val cfg = Config(
            discord = DiscordConfig(token = "super-secret-token-value-12345", homeGuildId = 100L),
            database = DatabaseConfig(path = "civdiscord.db"),
            patreon = PatreonConfig(
                accessToken = "patreon-secret-access-token-abcdef",
                campaignId = "campaign-1",
                tierRoles = mapOf("gold" to 11L),
                syncIntervalMinutes = 60,
            ),
        )
        cfg.toString() shouldNotContain "super-secret-token-value-12345"
        cfg.toString() shouldNotContain "patreon-secret-access-token-abcdef"
    }
}
