package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.application.EmailVerificationService
import io.github.chan808.authtemplate.member.application.MemberCommandService
import io.github.chan808.authtemplate.member.application.SignupRateLimitService
import io.github.chan808.authtemplate.member.presentation.SignupRequest
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
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MemberCommandServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val signupRateLimitService: SignupRateLimitService = mockk()
    private val breachedPasswordChecker: BreachedPasswordChecker = mockk()
    private val emailVerificationService: EmailVerificationService = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val domainMetrics: DomainMetrics = mockk(relaxed = true)
    private val memberCommandService = MemberCommandService(
        memberRepository,
        passwordEncoder,
        signupRateLimitService,
        breachedPasswordChecker,
        emailVerificationService,
        eventPublisher,
        domainMetrics,
    )

    @Test
    fun `duplicate verified email returns silently without side effects (user enumeration prevention)`() {
        every { signupRateLimitService.check(any()) } just Runs
        val existing = Member(email = "test@example.com", password = "encoded", emailVerified = true, id = 1L)
        every { memberRepository.findByEmailAndWithdrawnAtIsNull("test@example.com") } returns existing

        // 예외가 발생하지 않고 정상 종료되어야 한다.
        memberCommandService.signup(SignupRequest("test@example.com", "Password1!"), "127.0.0.1")

        // 기존 비밀번호가 그대로여야 하고, 어떤 메일도 발송되지 않아야 한다.
        assertEquals("encoded", existing.password)
        verify(exactly = 0) { emailVerificationService.sendVerification(any(), any()) }
        verify(exactly = 0) { memberRepository.save(any()) }
        verify(exactly = 0) { breachedPasswordChecker.check(any(), any()) }
        verify(exactly = 0) { passwordEncoder.encode(any()) }
    }

    @Test
    fun `email is normalized to lowercase on signup`() {
        every { signupRateLimitService.check(any()) } just Runs
        every { memberRepository.findByEmailAndWithdrawnAtIsNull("test@example.com") } returns null
        every { breachedPasswordChecker.check(any(), any()) } just Runs
        every { passwordEncoder.encode(any()) } returns "encoded"
        every { memberRepository.save(any()) } answers {
            firstArg<Member>().let { it.copyForTest(id = 1L) }
        }
        every { emailVerificationService.sendVerification(any(), any()) } just Runs

        memberCommandService.signup(SignupRequest("TEST@EXAMPLE.COM", "Password1!"), "127.0.0.1")

        verify { memberRepository.save(match { it.email == "test@example.com" }) }
    }

    @Test
    fun `unverified local account can sign up again and receives a new verification mail`() {
        val existing = Member(
            email = "test@example.com",
            password = "old-encoded",
            emailVerified = false,
            id = 1L,
        )
        every { signupRateLimitService.check(any()) } just Runs
        every { memberRepository.findByEmailAndWithdrawnAtIsNull("test@example.com") } returns existing
        every { emailVerificationService.sendVerification(1L, "test@example.com") } just Runs

        memberCommandService.signup(SignupRequest("test@example.com", "Password1!"), "127.0.0.1")

        assertEquals("old-encoded", existing.password)
        verify { emailVerificationService.sendVerification(1L, "test@example.com") }
        verify(exactly = 0) { memberRepository.save(any()) }
        verify(exactly = 0) { breachedPasswordChecker.check(any(), any()) }
        verify(exactly = 0) { passwordEncoder.encode(any()) }
    }

    @Test
    fun `missing member id throws member not found`() {
        every { memberRepository.findByIdAndWithdrawnAtIsNull(999L) } returns null

        val ex = assertThrows<MemberException> { memberCommandService.getById(999L) }
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `withdraw anonymizes member and keeps row for soft delete`() {
        val member = Member(
            email = "test@example.com",
            password = "encoded",
            provider = "GOOGLE",
            providerId = "provider-user-id",
            nickname = "tester",
            emailVerified = true,
            id = 1L,
        )
        every { memberRepository.findByIdAndWithdrawnAtIsNull(1L) } returns member
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        memberCommandService.withdraw(1L)

        assertNotEquals("test@example.com", member.email)
        assertNotEquals("provider-user-id", member.providerId)
        assertNull(member.password)
        assertNull(member.nickname)
        assertEquals(false, member.emailVerified)
        assertNotNull(member.withdrawnAt)
        verify(exactly = 0) { memberRepository.delete(any()) }
    }

    private fun Member.copyForTest(id: Long): Member = Member(
        email = this.email,
        password = this.password,
        emailVerified = this.emailVerified,
        provider = this.provider,
        providerId = this.providerId,
        nickname = this.nickname,
        role = this.role,
        withdrawnAt = this.withdrawnAt,
        id = id,
    )
}
