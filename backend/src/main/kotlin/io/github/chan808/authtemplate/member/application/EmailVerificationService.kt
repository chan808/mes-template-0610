package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.maskEmail
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.domain.event.MemberRegisteredEvent
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.redis.EmailVerificationStore
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EmailVerificationService(
    private val memberRepository: MemberRepository,
    private val emailVerificationStore: EmailVerificationStore,
    private val eventPublisher: ApplicationEventPublisher,
    private val resendRateLimitService: EmailVerificationResendRateLimitService,
    private val domainMetrics: DomainMetrics,
) {
    private val log = LoggerFactory.getLogger(EmailVerificationService::class.java)

    // 호출 측이 @Transactional 범위 내에 있어야 @TransactionalEventListener(AFTER_COMMIT) 정상 발화
    fun sendVerification(memberId: Long, email: String) {
        val token = UUID.randomUUID().toString()
        emailVerificationStore.save(token, memberId, ttlSeconds = 24L * 3600)
        eventPublisher.publishEvent(MemberRegisteredEvent(email, token))
    }

    @Transactional
    fun resend(email: String, ip: String) {
        val normalizedEmail = email.lowercase().trim()
        resendRateLimitService.check(ip, normalizedEmail)

        val member = memberRepository.findByEmailAndWithdrawnAtIsNull(normalizedEmail) ?: run {
            domainMetrics.recordEmailVerificationResend("ignored_unknown_email")
            return
        }

        if (member.emailVerified) {
            domainMetrics.recordEmailVerificationResend("ignored_verified")
            return
        }

        if (member.isOAuthAccount) {
            domainMetrics.recordEmailVerificationResend("ignored_oauth_account")
            log.info("[AUTH] email verification resend ignored email={} reason=OAUTH_ACCOUNT", maskEmail(normalizedEmail))
            return
        }

        sendVerification(member.id, member.email)
        domainMetrics.recordEmailVerificationResend("issued")
        log.info("[AUTH] email verification resent memberId={}", member.id)
    }

    @Transactional
    fun verify(token: String) {
        val memberId = emailVerificationStore.findMemberId(token) ?: run {
            log.warn("[AUTH] 이메일 인증 실패 reason=INVALID_TOKEN")
            throw MemberException(ErrorCode.VERIFICATION_TOKEN_INVALID)
        }
        val member = memberRepository.findByIdAndWithdrawnAtIsNull(memberId)
            ?: throw MemberException(ErrorCode.MEMBER_NOT_FOUND)
        if (member.emailVerified) {
            log.warn("[AUTH] 이메일 인증 실패 reason=ALREADY_VERIFIED memberId={}", memberId)
            throw MemberException(ErrorCode.EMAIL_ALREADY_VERIFIED)
        }
        member.emailVerified = true
        emailVerificationStore.delete(token)
        log.info("[AUTH] 이메일 인증 완료 memberId={}", memberId)
    }
}
