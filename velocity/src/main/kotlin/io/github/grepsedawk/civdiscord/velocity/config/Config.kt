package io.github.grepsedawk.civdiscord.velocity.config

data class DiscordConfig(val token: String, val homeGuildId: Long) {
    override fun toString(): String = "DiscordConfig(token=[REDACTED], homeGuildId=$homeGuildId)"
}

data class DatabaseConfig(val path: String)

data class BridgeConfig(val hmacEnabled: Boolean = false)

data class PatreonConfig(
    val accessToken: String,
    val campaignId: String,
    val tierRoles: Map<String, Long>,
    val syncIntervalMinutes: Int,
    val refreshToken: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
) {
    override fun toString(): String {
        val refreshTag = if (refreshToken == null) "null" else "[REDACTED]"
        val clientIdTag = if (clientId == null) "null" else "[REDACTED]"
        val clientSecretTag = if (clientSecret == null) "null" else "[REDACTED]"
        return "PatreonConfig(accessToken=[REDACTED], campaignId=$campaignId, tierRoles=$tierRoles, " +
            "syncIntervalMinutes=$syncIntervalMinutes, refreshToken=$refreshTag, " +
            "clientId=$clientIdTag, clientSecret=$clientSecretTag)"
    }
}

data class Config(
    val discord: DiscordConfig,
    val database: DatabaseConfig,
    val patreon: PatreonConfig?,
    val bridge: BridgeConfig = BridgeConfig(),
)
