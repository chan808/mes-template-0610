package io.github.chan808.authtemplate.user.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.UserException
import io.github.chan808.authtemplate.common.maskEmail
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.user.domain.event.UserRegisteredEvent
import io.github.chan808.authtemplate.user.infrastructure.persistence.UserRepository
import io.github.chan808.authtemplate.user.infrastructure.redis.EmailVerificationStore
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EmailVerificationService(
    private val userRepository: UserRepository,
    private val emailVerificationStore: EmailVerificationStore,
    private val eventPublisher: ApplicationEventPublisher,
    private val resendRateLimitService: EmailVerificationResendRateLimitService,
    private val domainMetrics: DomainMetrics,
) {
    private val log = LoggerFactory.getLogger(EmailVerificationService::class.java)

    // 호출 측이 @Transactional 범위 내에 있어야 @TransactionalEventListener(AFTER_COMMIT) 정상 발화
    fun sendVerification(userId: Long, email: String) {
        val token = UUID.randomUUID().toString()
        emailVerificationStore.save(token, userId, ttlSeconds = 24L * 3600)
        eventPublisher.publishEvent(UserRegisteredEvent(email, token))
    }

    @Transactional
    fun resend(email: String, ip: String) {
        val normalizedEmail = email.lowercase().trim()
        resendRateLimitService.check(ip, normalizedEmail)

        val user = userRepository.findByEmailAndWithdrawnAtIsNull(normalizedEmail) ?: run {
            domainMetrics.recordEmailVerificationResend("ignored_unknown_email")
            return
        }
        if (user.emailVerified) {
            domainMetrics.recordEmailVerificationResend("ignored_verified")
            return
        }
        if (user.isOAuthAccount) {
            domainMetrics.recordEmailVerificationResend("ignored_oauth_account")
            log.info("[AUTH] email verification resend ignored email={} reason=OAUTH_ACCOUNT", maskEmail(normalizedEmail))
            return
        }
        sendVerification(user.id, user.email)
        domainMetrics.recordEmailVerificationResend("issued")
        log.info("[AUTH] email verification resent userId={}", user.id)
    }

    @Transactional
    fun verify(token: String) {
        val userId = emailVerificationStore.findUserId(token) ?: run {
            log.warn("[AUTH] 이메일 인증 실패 reason=INVALID_TOKEN")
            throw UserException(ErrorCode.VERIFICATION_TOKEN_INVALID)
        }
        val user = userRepository.findByIdAndWithdrawnAtIsNull(userId)
            ?: throw UserException(ErrorCode.USER_NOT_FOUND)
        if (user.emailVerified) {
            log.warn("[AUTH] 이메일 인증 실패 reason=ALREADY_VERIFIED userId={}", userId)
            throw UserException(ErrorCode.EMAIL_ALREADY_VERIFIED)
        }
        user.emailVerified = true
        emailVerificationStore.delete(token)
        log.info("[AUTH] 이메일 인증 완료 userId={}", userId)
    }
}
