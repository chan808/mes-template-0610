package io.github.chan808.authtemplate.common.security

import io.github.chan808.authtemplate.auth.infrastructure.security.JwtProperties
import io.github.chan808.authtemplate.auth.infrastructure.security.JwtProvider
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class JwtProviderTest {

    private val props = JwtProperties(
        secret = "test-secret-key-must-be-at-least-32-bytes-long-for-hs256",
        accessTokenExpiry = 1800,
        refreshTokenExpiry = 604800,
    )
    private val jwtProvider = JwtProvider(props)

    @Test
    fun `access token contains member identity role and token version`() {
        val token = jwtProvider.generateAccessToken(1L, "USER", 3L)
        val claims = jwtProvider.validate(token)

        assertEquals("1", claims.subject)
        assertEquals("USER", claims["role"])
        assertEquals(3, claims["tokenVersion"])
    }

    @Test
    fun `tampered token throws jwt exception`() {
        val token = jwtProvider.generateAccessToken(1L, "USER", 0L)
        assertThrows<JwtException> { jwtProvider.validate(token.dropLast(10) + "TAMPERED!!") }
    }

    @Test
    fun `expired token throws expired jwt exception`() {
        val expiredProvider = JwtProvider(props.copy(accessTokenExpiry = -1))
        val token = expiredProvider.generateAccessToken(1L, "USER", 0L)
        assertThrows<ExpiredJwtException> { jwtProvider.validate(token) }
    }
}
