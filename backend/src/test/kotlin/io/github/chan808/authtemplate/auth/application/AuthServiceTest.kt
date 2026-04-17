package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.application.AuthCommandService
import io.github.chan808.authtemplate.auth.application.LoginRateLimitService
import io.github.chan808.authtemplate.auth.application.port.AccessTokenPort
import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import io.github.chan808.authtemplate.auth.presentation.LoginRequest
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.user.api.AuthUserView
import io.github.chan808.authtemplate.user.api.UserApi
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthCommandServiceTest {

    private val userApi: UserApi = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val accessTokenPort: AccessTokenPort = mockk()
    private val tokenStore: TokenStore = mockk()
    private val loginRateLimitService: LoginRateLimitService = mockk()
    private val domainMetrics: DomainMetrics = mockk(relaxed = true)
    private val authCommandService = AuthCommandService(
        userApi,
        passwordEncoder,
        accessTokenPort,
        tokenStore,
        loginRateLimitService,
        domainMetrics,
    )

    private val userView = AuthUserView(
        id = 1L,
        email = "test@example.com",
        encodedPassword = "encoded-password",
        role = "USER",
        tokenVersion = 0L,
        emailVerified = true,
        provider = null,
    )

    @Test
    fun `valid credentials return access token and refresh token`() {
        every { loginRateLimitService.check(any(), any()) } just Runs
        every { userApi.findAuthUserByEmail("test@example.com") } returns userView
        every { passwordEncoder.matches(any(), any()) } returns true
        every { accessTokenPort.generateAccessToken(1L, "USER", 0L) } returns "access-token"
        every { tokenStore.save(any(), any(), any()) } just Runs
        every { tokenStore.addSession(any(), any()) } just Runs

        val (at, rt) = authCommandService.login(LoginRequest("test@example.com", "password123"), "127.0.0.1")

        assertEquals("access-token", at)
        assertTrue(rt.contains('.'))
    }

    @Test
    fun `invalid password throws invalid credentials`() {
        every { loginRateLimitService.check(any(), any()) } just Runs
        every { userApi.findAuthUserByEmail(any()) } returns userView
        every { passwordEncoder.matches(any(), any()) } returns false

        val ex = assertThrows<AuthException> {
            authCommandService.login(LoginRequest("test@example.com", "wrong-password"), "127.0.0.1")
        }
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.errorCode)
    }

    @Test
    fun `reissue lock conflict throws reissue conflict`() {
        every { tokenStore.tryLock(any()) } returns false

        val ex = assertThrows<AuthException> { authCommandService.reissue("${UUID.randomUUID()}.randompart") }

        assertEquals(ErrorCode.REISSUE_CONFLICT, ex.errorCode)
    }

    @Test
    fun `refresh token mismatch revokes all user sessions and throws mismatch`() {
        val sid = UUID.randomUUID().toString()
        val session = RefreshTokenSession(
            userId = 1L,
            role = "USER",
            tokenHash = "wrong-hash",
            absoluteExpiryEpoch = Instant.now().plusSeconds(3600).epochSecond,
        )
        every { tokenStore.tryLock(sid) } returns true
        every { tokenStore.find(sid) } returns session
        every { tokenStore.deleteAllSessionsForUser(1L) } just Runs
        every { tokenStore.releaseLock(sid) } just Runs

        val ex = assertThrows<AuthException> { authCommandService.reissue("$sid.actualrandompart") }

        assertEquals(ErrorCode.REFRESH_TOKEN_MISMATCH, ex.errorCode)
        verify { tokenStore.deleteAllSessionsForUser(1L) }
    }

    @Test
    fun `logout removes session from token store`() {
        val sid = UUID.randomUUID().toString()
        val session = RefreshTokenSession(
            userId = 1L,
            role = "USER",
            tokenHash = "hash-value",
            absoluteExpiryEpoch = Instant.now().plusSeconds(3600).epochSecond,
        )
        every { tokenStore.find(sid) } returns session
        every { tokenStore.deleteSession(1L, sid) } just Runs

        authCommandService.logout("$sid.randompart")

        verify { tokenStore.deleteSession(1L, sid) }
    }
}
