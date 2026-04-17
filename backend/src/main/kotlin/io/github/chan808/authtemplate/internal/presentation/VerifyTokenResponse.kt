package io.github.chan808.authtemplate.internal.presentation

data class VerifyTokenResponse(
    val userId: Long,
    val nickname: String?,
    val avatarId: Long?,
)
