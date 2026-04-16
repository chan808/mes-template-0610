package io.github.chan808.authtemplate.auth.application.port

interface AccessTokenPort {
    fun generateAccessToken(memberId: Long, role: String, tokenVersion: Long): String
    fun getMemberId(token: String): Long
}
