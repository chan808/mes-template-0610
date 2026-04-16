package io.github.chan808.authtemplate.auth.application.port

interface PasswordResetTokenStore {
    fun save(token: String, memberId: Long)
    fun consume(token: String): Long?
}
