package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.security.BreachedPasswordChecker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class MemberAuthFacadeTest {

    private val memberRepository: MemberRepository = mockk()
    private val emailVerificationService: EmailVerificationService = mockk()
    private val breachedPasswordChecker: BreachedPasswordChecker = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val domainMetrics: DomainMetrics = mockk(relaxed = true)
    private val service = MemberAuthFacade(
        memberRepository,
        emailVerificationService,
        breachedPasswordChecker,
        passwordEncoder,
        eventPublisher,
        domainMetrics,
    )

    @Test
    fun `oauth account cannot reset password`() {
        val oauthMember = Member(
            email = "oauth@example.com",
            provider = "GOOGLE",
            providerId = "123",
            emailVerified = true,
            id = 1L,
        )
        every { memberRepository.findByIdAndWithdrawnAtIsNull(1L) } returns oauthMember

        val ex = assertThrows<AuthException> { service.resetPassword(1L, "NewPass1!") }

        assertEquals(ErrorCode.OAUTH_PASSWORD_RESET_NOT_ALLOWED, ex.errorCode)
        verify(exactly = 0) { breachedPasswordChecker.check(any(), any()) }
    }

    @Test
    fun `local account reset password updates password and publishes event`() {
        val member = Member(
            email = "local@example.com",
            password = "old-password",
            emailVerified = true,
            id = 1L,
        )
        every { memberRepository.findByIdAndWithdrawnAtIsNull(1L) } returns member
        every { breachedPasswordChecker.check(any(), any()) } just Runs
        every { passwordEncoder.encode("NewPass1!") } returns "encoded-password"
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        service.resetPassword(1L, "NewPass1!")

        assertEquals("encoded-password", member.password)
        verify { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `verifyEmail uses writable transaction`() {
        val method = MemberAuthFacade::class.java.getMethod("verifyEmail", String::class.java)

        val tx = method.getAnnotation(Transactional::class.java)

        assertNotNull(tx)
        assertFalse(tx.readOnly)
    }

    @Test
    fun `resendVerification delegates to email verification service`() {
        every { emailVerificationService.resend("test@example.com", "127.0.0.1") } just Runs

        service.resendVerification("test@example.com", "127.0.0.1")

        verify { emailVerificationService.resend("test@example.com", "127.0.0.1") }
    }
}
