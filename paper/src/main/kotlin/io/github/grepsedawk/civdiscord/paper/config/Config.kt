package io.github.grepsedawk.civdiscord.paper.config

data class BridgeConfig(val hmacEnabled: Boolean = true)

data class Config(val serverName: String, val bridge: BridgeConfig = BridgeConfig())
