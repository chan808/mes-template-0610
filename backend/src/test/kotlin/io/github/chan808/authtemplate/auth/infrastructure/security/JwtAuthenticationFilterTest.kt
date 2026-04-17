package io.github.chan808.authtemplate.auth.infrastructure.security

import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.user.api.AuthUserView
import io.github.chan808.authtemplate.user.api.UserApi
import io.jsonwebtoken.Claims
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtAuthenticationFilterTest {

    private val jwtProvider: JwtProvider = mockk()
    private val userApi: UserApi = mockk()
    private val tokenStore: TokenStore = mockk()
    private val filter = JwtAuthenticationFilter(jwtProvider, userApi, tokenStore)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `cached token version match authenticates request`() {
        every { jwtProvider.validate("valid-token") } returns claims(userId = 1L, role = "USER", tokenVersion = 2L)
        every { tokenStore.findAccessTokenVersion(1L) } returns 2L

        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer valid-token")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        assertEquals(1L, SecurityContextHolder.getContext().authentication?.principal)
        assertNull(request.getAttribute("jwt-error"))
    }

    @Test
    fun `cache miss loads token version from user api and caches it`() {
        every { jwtProvider.validate("valid-token") } returns claims(userId = 1L, role = "USER", tokenVersion = 3L)
        every { tokenStore.findAccessTokenVersion(1L) } returns null
        every { userApi.findAuthUserById(1L) } returns authUserView(tokenVersion = 3L)
        every { tokenStore.cacheAccessTokenVersion(1L, 3L) } just runs

        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer valid-token")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        assertEquals(1L, SecurityContextHolder.getContext().authentication?.principal)
        assertNull(request.getAttribute("jwt-error"))
    }

    @Test
    fun `mismatched token version marks token invalid`() {
        every { jwtProvider.validate("stale-token") } returns claims(userId = 1L, role = "USER", tokenVersion = 1L)
        every { tokenStore.findAccessTokenVersion(1L) } returns 2L

        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer stale-token")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.TOKEN_INVALID, request.getAttribute("jwt-error"))
    }

    @Test
    fun `cache miss with missing user marks token invalid`() {
        every { jwtProvider.validate("withdrawn-token") } returns claims(userId = 1L, role = "USER", tokenVersion = 0L)
        every { tokenStore.findAccessTokenVersion(1L) } returns null
        every { userApi.findAuthUserById(1L) } returns null

        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer withdrawn-token")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.TOKEN_INVALID, request.getAttribute("jwt-error"))
    }

    private fun claims(userId: Long, role: String, tokenVersion: Long): Claims {
        val claims: Claims = mockk()
        every { claims.subject } returns userId.toString()
        every { claims["role"] } returns role
        every { claims["tokenVersion"] } returns tokenVersion
        return claims
    }

    private fun authUserView(tokenVersion: Long) = AuthUserView(
        id = 1L,
        email = "user@example.com",
        encodedPassword = "encoded",
        role = "USER",
        tokenVersion = tokenVersion,
        emailVerified = true,
        provider = null,
    )
}
