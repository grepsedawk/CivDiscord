package io.github.grepsedawk.civdiscord.paper.config

data class BridgeConfig(val hmacEnabled: Boolean = false)

data class Config(val serverName: String, val bridge: BridgeConfig = BridgeConfig())
