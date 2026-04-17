package io.github.chan808.agolive.user.api

interface UserApi {
    fun findAuthUserByEmail(email: String): AuthUserView?
    fun findAuthUserById(id: Long): AuthUserView?
    fun verifyEmail(token: String)
    fun resendVerification(email: String, ip: String)
    fun resetPassword(userId: Long, newRawPassword: String)
    fun findOrCreateOAuthUser(email: String, provider: String, providerId: String, nickname: String?): AuthUserView
}
