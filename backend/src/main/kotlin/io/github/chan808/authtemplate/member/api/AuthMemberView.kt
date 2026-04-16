package io.github.chan808.authtemplate.member.api

data class AuthMemberView(
    val id: Long,
    val email: String,
    val encodedPassword: String?,
    val role: String,
    val tokenVersion: Long,
    val emailVerified: Boolean,
    val provider: String?,
) {
    val isOAuthAccount: Boolean get() = provider != null
}
