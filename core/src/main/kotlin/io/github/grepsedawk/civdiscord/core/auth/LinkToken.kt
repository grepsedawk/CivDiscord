package io.github.grepsedawk.civdiscord.core.auth

import java.util.UUID

data class LinkToken(
    val code: String,
    val mcUuid: UUID,
    val mcName: String,
    val expiresAt: Long,
)
