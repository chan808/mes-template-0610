package io.github.chan808.authtemplate.auth.infrastructure.security

import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.member.api.AuthMemberView
import io.github.chan808.authtemplate.member.api.MemberApi
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
    private val memberApi: MemberApi = mockk()
    private val tokenStore: TokenStore = mockk()
    private val filter = JwtAuthenticationFilter(jwtProvider, memberApi, tokenStore)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `cached token version match authenticates request`() {
        every { jwtProvider.validate("valid-token") } returns claims(memberId = 1L, role = "USER", tokenVersion = 2L)
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
    fun `cache miss loads token version from member api and caches it`() {
        every { jwtProvider.validate("valid-token") } returns claims(memberId = 1L, role = "USER", tokenVersion = 3L)
        every { tokenStore.findAccessTokenVersion(1L) } returns null
        every { memberApi.findAuthMemberById(1L) } returns authMemberView(tokenVersion = 3L)
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
        every { jwtProvider.validate("stale-token") } returns claims(memberId = 1L, role = "USER", tokenVersion = 1L)
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
    fun `cache miss with missing member marks token invalid`() {
        every { jwtProvider.validate("withdrawn-token") } returns claims(memberId = 1L, role = "USER", tokenVersion = 0L)
        every { tokenStore.findAccessTokenVersion(1L) } returns null
        every { memberApi.findAuthMemberById(1L) } returns null

        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer withdrawn-token")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.TOKEN_INVALID, request.getAttribute("jwt-error"))
    }

    private fun claims(memberId: Long, role: String, tokenVersion: Long): Claims {
        val claims: Claims = mockk()
        every { claims.subject } returns memberId.toString()
        every { claims["role"] } returns role
        every { claims["tokenVersion"] } returns tokenVersion
        return claims
    }

    private fun authMemberView(tokenVersion: Long) = AuthMemberView(
        id = 1L,
        email = "member@example.com",
        encodedPassword = "encoded",
        role = "USER",
        tokenVersion = tokenVersion,
        emailVerified = true,
        provider = null,
    )
}
