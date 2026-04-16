package io.github.chan808.authtemplate.auth.presentation

import com.ninjasquad.springmockk.MockkBean
import io.github.chan808.authtemplate.auth.application.AuthCommandService
import io.github.chan808.authtemplate.auth.application.PasswordResetService
import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.auth.infrastructure.redis.OAuthCodeStore
import io.github.chan808.authtemplate.auth.infrastructure.security.JwtProvider
import io.github.chan808.authtemplate.auth.infrastructure.security.SecurityConfig
import io.github.chan808.authtemplate.auth.infrastructure.security.SecurityExceptionHandler
import io.github.chan808.authtemplate.auth.presentation.AuthController
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ClientIpResolver
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.RateLimitException
import io.github.chan808.authtemplate.member.api.MemberApi
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class)
@TestPropertySource(
    properties = [
        "jwt.refresh-token-expiry=604800",
        "cookie.secure=false",
        "cors.allowed-origin=http://localhost:3000",
    ],
)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean lateinit var authCommandService: AuthCommandService
    @MockkBean lateinit var memberApi: MemberApi
    @MockkBean lateinit var passwordResetService: PasswordResetService
    @MockkBean lateinit var clientIpResolver: ClientIpResolver
    @MockkBean lateinit var oAuthCodeStore: OAuthCodeStore
    @MockkBean(relaxed = true) lateinit var tokenStore: TokenStore
    @MockkBean lateinit var jwtProvider: JwtProvider
    @MockkBean(relaxed = true) lateinit var securityExceptionHandler: SecurityExceptionHandler

    @BeforeEach
    fun setUp() {
        every { clientIpResolver.resolve(any()) } returns "127.0.0.1"
    }

    @Test
    fun `login returns access token and refresh token cookie`() {
        every { authCommandService.login(any(), any()) } returns ("access-token" to "sid.randompart")

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"password123"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("access-token") }
            header { string("Cache-Control", "no-store") }
            header { string("Pragma", "no-cache") }
            header { string("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'") }
            header { string("Permissions-Policy", "camera=(), microphone=(), geolocation=()") }
            header { string("X-Frame-Options", "DENY") }
            header { string("X-Content-Type-Options", "nosniff") }
            cookie { exists("refresh_token") }
            cookie { httpOnly("refresh_token", true) }
        }
    }

    @Test
    fun `invalid login payload returns 400`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"not-an-email","password":"password123"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `malformed login payload returns 400`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `unsupported login method returns 405`() {
        mockMvc.get("/api/auth/login").andExpect {
            status { isMethodNotAllowed() }
        }
    }

    @Test
    fun `invalid credentials return 401`() {
        every { authCommandService.login(any(), any()) } throws AuthException(ErrorCode.INVALID_CREDENTIALS)

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"wrong"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `rate limited login returns 429`() {
        every { authCommandService.login(any(), any()) } throws RateLimitException(retryAfterSeconds = 60L)

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"password123"}"""
        }.andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "60") }
        }
    }

    @Test
    fun `reissue returns new tokens`() {
        every { authCommandService.reissue(any()) } returns ("new-access-token" to "new-sid.randompart")

        mockMvc.post("/api/auth/reissue") {
            cookie(Cookie("refresh_token", "old-sid.randompart"))
            header("X-CSRF-GUARD", "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("new-access-token") }
            header { string("Cache-Control", "no-store") }
            header { string("Pragma", "no-cache") }
            cookie { exists("refresh_token") }
        }
    }

    @Test
    fun `missing csrf guard on reissue returns 400`() {
        mockMvc.post("/api/auth/reissue") {
            cookie(Cookie("refresh_token", "sid.randompart"))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `missing refresh token on reissue returns 401`() {
        mockMvc.post("/api/auth/reissue") {
            header("X-CSRF-GUARD", "1")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `logout expires refresh token cookie`() {
        every { authCommandService.logout(any()) } just Runs

        mockMvc.post("/api/auth/logout") {
            cookie(Cookie("refresh_token", "sid.randompart"))
            header("X-CSRF-GUARD", "1")
        }.andExpect {
            status { isOk() }
            cookie { maxAge("refresh_token", 0) }
        }
    }

    @Test
    fun `missing csrf guard on logout returns 400`() {
        mockMvc.post("/api/auth/logout") {
            cookie(Cookie("refresh_token", "sid.randompart"))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `verify email delegates to member api`() {
        every { memberApi.verifyEmail("valid-token") } just Runs

        mockMvc.post("/api/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"valid-token"}"""
        }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `invalid verification token returns 400`() {
        every { memberApi.verifyEmail("bad-token") } throws MemberException(ErrorCode.VERIFICATION_TOKEN_INVALID)

        mockMvc.post("/api/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"bad-token"}"""
        }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `resend verification mail returns 200`() {
        every { memberApi.resendVerification("test@example.com", "127.0.0.1") } just Runs

        mockMvc.post("/api/auth/verify-email/resend") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `resend verification mail rate limit returns 429`() {
        every { memberApi.resendVerification("test@example.com", "127.0.0.1") } throws RateLimitException(retryAfterSeconds = 900L)

        mockMvc.post("/api/auth/verify-email/resend") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com"}"""
        }.andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "900") }
        }
    }

    @Test
    fun `password reset request always returns 200`() {
        every { passwordResetService.requestReset(any(), any()) } just Runs

        mockMvc.post("/api/auth/password-reset/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"unknown@example.com"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `password reset confirm returns 200`() {
        every { passwordResetService.confirmReset(any(), any()) } just Runs

        mockMvc.post("/api/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"valid-token","newPassword":"NewPass1!"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `expired password reset token returns 400`() {
        every { passwordResetService.confirmReset(any(), any()) } throws AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)

        mockMvc.post("/api/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"expired-token","newPassword":"NewPass1!"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("PASSWORD_RESET_TOKEN_INVALID") }
        }
    }

    @Test
    fun `oauth code exchange returns access token`() {
        every { oAuthCodeStore.findAndDelete("valid-code") } returns "access-token"

        mockMvc.get("/api/auth/oauth2/token?code=valid-code")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.accessToken") { value("access-token") }
                header { string("Cache-Control", "no-store") }
                header { string("Pragma", "no-cache") }
            }
    }

    @Test
    fun `missing oauth code returns 401`() {
        every { oAuthCodeStore.findAndDelete("expired-code") } returns null

        mockMvc.get("/api/auth/oauth2/token?code=expired-code")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.title") { value("OAUTH_CODE_NOT_FOUND") }
            }
    }
}
