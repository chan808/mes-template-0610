package io.github.chan808.agolive.user.api

data class AuthUserView(
    val id: Long,
    val email: String,
    val encodedPassword: String?,
    val role: String,
    val tokenVersion: Long,
    val emailVerified: Boolean,
    val provider: String?,
    val nickname: String?,
    val avatarId: Long?,
) {
    val isOAuthAccount: Boolean get() = provider != null
}
