package io.github.chan808.agolive.auth.application.port

interface PasswordResetTokenStore {
    fun save(token: String, userId: Long)
    fun consume(token: String): Long?
}
