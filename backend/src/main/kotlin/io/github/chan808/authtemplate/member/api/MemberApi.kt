package io.github.chan808.authtemplate.member.api

interface MemberApi {
    fun findAuthMemberByEmail(email: String): AuthMemberView?
    fun findAuthMemberById(id: Long): AuthMemberView?
    fun verifyEmail(token: String)
    fun resendVerification(email: String, ip: String)
    fun resetPassword(memberId: Long, newRawPassword: String)
    fun findOrCreateOAuthMember(email: String, provider: String, providerId: String, nickname: String?): AuthMemberView
}
