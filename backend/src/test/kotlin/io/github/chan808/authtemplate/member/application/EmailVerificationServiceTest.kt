package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.application.EmailVerificationService
import io.github.chan808.authtemplate.member.application.EmailVerificationResendRateLimitService
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.infrastructure.redis.EmailVerificationStore
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmailVerificationServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val emailVerificationStore: EmailVerificationStore = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val resendRateLimitService: EmailVerificationResendRateLimitService = mockk()
    private val domainMetrics: DomainMetrics = mockk(relaxed = true)
    private val service = EmailVerificationService(
        memberRepository,
        emailVerificationStore,
        eventPublisher,
        resendRateLimitService,
        domainMetrics,
    )

    private val member = Member(
        email = "test@example.com",
        emailVerified = false,
        id = 1L,
    )

    @Test
    fun `sendVerification 호출 시 토큰이 저장되고 이벤트가 발행된다`() {
        every { emailVerificationStore.save(any(), 1L, any()) } just Runs
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        service.sendVerification(1L, "test@example.com")

        // 토큰이 저장됐는지, 이벤트가 정확히 1회 발행됐는지 확인
        verify(exactly = 1) { emailVerificationStore.save(any(), 1L, any()) }
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `유효한 토큰으로 verify 호출 시 emailVerified가 true로 변경된다`() {
        every { emailVerificationStore.findMemberId("valid-token") } returns 1L
        every { memberRepository.findByIdAndWithdrawnAtIsNull(1L) } returns member
        every { emailVerificationStore.delete("valid-token") } just Runs

        service.verify("valid-token")

        assertTrue(member.emailVerified)
        verify(exactly = 1) { emailVerificationStore.delete("valid-token") }
    }

    @Test
    fun `존재하지 않는 토큰으로 verify 호출 시 VERIFICATION_TOKEN_INVALID 예외가 발생한다`() {
        every { emailVerificationStore.findMemberId("invalid-token") } returns null

        val ex = assertThrows<MemberException> { service.verify("invalid-token") }
        assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID, ex.errorCode)
    }

    @Test
    fun `이미 인증된 이메일로 verify 호출 시 EMAIL_ALREADY_VERIFIED 예외가 발생한다`() {
        val verifiedMember = Member(email = "test@example.com", emailVerified = true, id = 1L)
        every { emailVerificationStore.findMemberId("token") } returns 1L
        every { memberRepository.findByIdAndWithdrawnAtIsNull(1L) } returns verifiedMember

        val ex = assertThrows<MemberException> { service.verify("token") }
        assertEquals(ErrorCode.EMAIL_ALREADY_VERIFIED, ex.errorCode)
    }

    @Test
    fun `resend runs in a transaction so after-commit listeners can execute`() {
        val annotation = EmailVerificationService::class.java
            .getDeclaredMethod("resend", String::class.java, String::class.java)
            .getAnnotation(Transactional::class.java)

        assertNotNull(annotation)
    }

    @Test
    fun `resend issues a new verification mail for unverified local account`() {
        every { resendRateLimitService.check("127.0.0.1", "test@example.com") } just Runs
        every { memberRepository.findByEmailAndWithdrawnAtIsNull("test@example.com") } returns member
        every { emailVerificationStore.save(any(), 1L, any()) } just Runs
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        service.resend("test@example.com", "127.0.0.1")

        verify { resendRateLimitService.check("127.0.0.1", "test@example.com") }
        verify { emailVerificationStore.save(any(), 1L, any()) }
        verify { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `resend ignores already verified account`() {
        val verifiedMember = Member(email = "test@example.com", emailVerified = true, id = 1L)
        every { resendRateLimitService.check("127.0.0.1", "test@example.com") } just Runs
        every { memberRepository.findByEmailAndWithdrawnAtIsNull("test@example.com") } returns verifiedMember

        service.resend("test@example.com", "127.0.0.1")

        verify(exactly = 0) { emailVerificationStore.save(any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }
}
