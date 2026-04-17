package io.github.chan808.agolive.internal.presentation

data class VerifyTokenResponse(
    val userId: Long,
    val nickname: String?,
    val avatarId: Long?,
)
