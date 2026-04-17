package io.github.chan808.authtemplate.auth.application.port

interface AccessTokenPort {
    fun generateAccessToken(userId: Long, role: String, tokenVersion: Long): String
    fun getUserId(token: String): Long
}
