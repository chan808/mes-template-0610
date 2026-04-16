package io.github.chan808.authtemplate.member.presentation

import com.ninjasquad.springmockk.MockkBean
import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.auth.infrastructure.security.JwtProvider
import io.github.chan808.authtemplate.auth.infrastructure.security.SecurityConfig
import io.github.chan808.authtemplate.auth.infrastructure.security.SecurityExceptionHandler
import io.github.chan808.authtemplate.common.ClientIpResolver
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.RateLimitException
import io.github.chan808.authtemplate.member.api.AuthMemberView
import io.github.chan808.authtemplate.member.api.MemberApi
import io.github.chan808.authtemplate.member.application.MemberCommandService
import io.github.chan808.authtemplate.member.presentation.MemberController
import io.github.chan808.authtemplate.member.presentation.MemberResponse
import io.jsonwebtoken.Claims
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(MemberController::class)
@Import(SecurityConfig::class, SecurityExceptionHandler::class)
@TestPropertySource(
    properties = [
        "jwt.refresh-token-expiry=604800",
        "cookie.secure=false",
        "cors.allowed-origin=http://localhost:3000",
    ],
)
class MemberControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean lateinit var memberCommandService: MemberCommandService
    @MockkBean lateinit var memberApi: MemberApi
    @MockkBean lateinit var clientIpResolver: ClientIpResolver
    @MockkBean(relaxed = true) lateinit var tokenStore: TokenStore
    @MockkBean lateinit var jwtProvider: JwtProvider

    private val testMemberResponse = MemberResponse(
        id = 1L,
        email = "test@example.com",
        nickname = "tester",
        provider = null,
        role = "USER",
        createdAt = LocalDateTime.now(),
    )

    private val authHeader = "Bearer test-token"

    @BeforeEach
    fun setup() {
        every { clientIpResolver.resolve(any()) } returns "127.0.0.1"
        every { tokenStore.findAccessTokenVersion(any()) } returns null
        every { tokenStore.cacheAccessTokenVersion(any(), any()) } just Runs
        val claims = mockk<Claims>()
        every { claims.subject } returns "1"
        every { claims["role"] } returns "USER"
        every { claims["tokenVersion"] } returns 0L
        every { jwtProvider.validate("test-token") } returns claims
        every { memberApi.findAuthMemberById(1L) } returns AuthMemberView(
            id = 1L,
            email = "test@example.com",
            encodedPassword = "encoded-password",
            role = "USER",
            tokenVersion = 0L,
            emailVerified = true,
            provider = null,
        )
    }

    @Test
    fun `signup returns 201`() {
        every { memberCommandService.signup(any(), any()) } just Runs

        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"Password1!"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.email") { doesNotExist() }
            jsonPath("$.message") { exists() }
        }
    }

    // 주의: signup은 사용자 열거 방지를 위해 중복 가입 요청에도 201을 반환한다.
    // 기존 "duplicate email returns 409" 테스트는 열거 방지 정책 도입으로 제거됨.

    @Test
    fun `too short password returns 400`() {
        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"short"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `signup rate limit returns 429`() {
        every { memberCommandService.signup(any(), any()) } throws RateLimitException(retryAfterSeconds = 3600L)

        mockMvc.post("/api/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@example.com","password":"Password1!"}"""
        }.andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "3600") }
        }
    }

    @Test
    fun `get my info returns 200`() {
        every { memberCommandService.getMyInfo(1L) } returns testMemberResponse

        mockMvc.get("/api/members/me") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.email") { value("test@example.com") }
            jsonPath("$.data.role") { value("USER") }
        }
    }

    @Test
    fun `get my info without auth returns 401`() {
        mockMvc.get("/api/members/me")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `get my info with stale access token returns 401`() {
        every { memberApi.findAuthMemberById(1L) } returns AuthMemberView(
            id = 1L,
            email = "test@example.com",
            encodedPassword = "encoded-password",
            role = "USER",
            tokenVersion = 1L,
            emailVerified = true,
            provider = null,
        )

        mockMvc.get("/api/members/me") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("TOKEN_INVALID") }
        }
    }

    @Test
    fun `update profile returns 200`() {
        every { memberCommandService.updateProfile(1L, any()) } returns testMemberResponse.copy(nickname = "new-nickname")

        mockMvc.patch("/api/members/me/profile") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"new-nickname"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.nickname") { value("new-nickname") }
        }
    }

    @Test
    fun `too long nickname returns 400`() {
        mockMvc.patch("/api/members/me/profile") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"${"a".repeat(51)}"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `change password returns 200`() {
        every { memberCommandService.changePassword(1L, any()) } just Runs

        mockMvc.patch("/api/members/me/password") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"OldPass1!","newPassword":"NewPass1!"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `invalid current password returns 400`() {
        every { memberCommandService.changePassword(1L, any()) } throws MemberException(ErrorCode.INVALID_CURRENT_PASSWORD)

        mockMvc.patch("/api/members/me/password") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"WrongPass1!","newPassword":"NewPass1!"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("INVALID_CURRENT_PASSWORD") }
        }
    }

    @Test
    fun `withdraw returns 200 and expires refresh token cookie`() {
        every { memberCommandService.withdraw(1L) } just Runs

        mockMvc.delete("/api/members/me") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            cookie { maxAge("refresh_token", 0) }
        }
    }

    @Test
    fun `withdraw without auth returns 401`() {
        mockMvc.delete("/api/members/me")
            .andExpect { status { isUnauthorized() } }
    }
}
