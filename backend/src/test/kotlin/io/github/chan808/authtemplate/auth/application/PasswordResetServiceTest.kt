package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.application.PasswordResetRateLimitService
import io.github.chan808.authtemplate.auth.application.PasswordResetService
import io.github.chan808.authtemplate.auth.application.port.AuthMailSender
import io.github.chan808.authtemplate.auth.application.port.PasswordResetTokenStore
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.api.AuthMemberView
import io.github.chan808.authtemplate.member.api.MemberApi
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PasswordResetServiceTest {

    private val memberApi: MemberApi = mockk()
    private val passwordResetStore: PasswordResetTokenStore = mockk()
    private val mailSender: AuthMailSender = mockk()
    private val passwordResetRateLimitService: PasswordResetRateLimitService = mockk()
    private val domainMetrics: DomainMetrics = mockk(relaxed = true)
    private val service = PasswordResetService(
        memberApi,
        passwordResetStore,
        mailSender,
        passwordResetRateLimitService,
        domainMetrics,
        "https://example.com",
        "ko",
    )

    private val localMember = AuthMemberView(
        id = 1L,
        email = "test@example.com",
        encodedPassword = "encoded-old-password",
        role = "USER",
        tokenVersion = 0L,
        emailVerified = true,
        provider = null,
    )

    private val oauthMember = localMember.copy(provider = "GOOGLE")

    @Test
    fun `request reset stores token and sends email for local account`() {
        every { passwordResetRateLimitService.check(any(), any()) } just Runs
        every { memberApi.findAuthMemberByEmail("test@example.com") } returns localMember
        every { passwordResetStore.save(any(), 1L) } just Runs
        every { mailSender.send(any(), any(), any()) } just Runs

        service.requestReset("test@example.com", "127.0.0.1")

        verify { passwordResetRateLimitService.check("127.0.0.1", "test@example.com") }
        verify { passwordResetStore.save(any(), 1L) }
        verify {
            mailSender.send(
                "test@example.com",
                "Password reset",
                match { it.contains("https://example.com/ko/reset-password?token=") },
            )
        }
    }

    @Test
    fun `request reset on unknown email returns silently`() {
        every { passwordResetRateLimitService.check(any(), any()) } just Runs
        every { memberApi.findAuthMemberByEmail(any()) } returns null

        service.requestReset("unknown@example.com", "127.0.0.1")

        verify { passwordResetRateLimitService.check("127.0.0.1", "unknown@example.com") }
        verify(exactly = 0) { passwordResetStore.save(any(), any()) }
        verify(exactly = 0) { mailSender.send(any(), any(), any()) }
    }

    @Test
    fun `request reset on oauth account does not issue token`() {
        every { passwordResetRateLimitService.check(any(), any()) } just Runs
        every { memberApi.findAuthMemberByEmail("oauth@example.com") } returns oauthMember.copy(email = "oauth@example.com")

        service.requestReset("oauth@example.com", "127.0.0.1")

        verify { passwordResetRateLimitService.check("127.0.0.1", "oauth@example.com") }
        verify(exactly = 0) { passwordResetStore.save(any(), any()) }
        verify(exactly = 0) { mailSender.send(any(), any(), any()) }
    }

    @Test
    fun `confirm reset consumes token and delegates to memberApi`() {
        every { passwordResetStore.consume("valid-token") } returns 1L
        every { memberApi.resetPassword(1L, "new-password") } just Runs

        service.confirmReset("valid-token", "new-password")

        verify { passwordResetStore.consume("valid-token") }
        verify { memberApi.resetPassword(1L, "new-password") }
    }

    @Test
    fun `confirm reset with invalid token throws exception`() {
        every { passwordResetStore.consume("expired-token") } returns null

        val ex = assertThrows<AuthException> { service.confirmReset("expired-token", "new-password") }
        assertEquals(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, ex.errorCode)
    }
}
