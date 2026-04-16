package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.RateLimitException
import io.github.chan808.authtemplate.common.ratelimit.RateLimiter
import io.github.chan808.authtemplate.common.maskEmail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EmailVerificationResendRateLimitService(
    private val rateLimiter: RateLimiter,
) {

    private val log = LoggerFactory.getLogger(EmailVerificationResendRateLimitService::class.java)

    fun check(ip: String, email: String) {
        rateLimiter.retryAfterIfExceeded("RATE:EMAIL_VERIFY_RESEND:IP:$ip", ttlSeconds = 3600, limit = 10)
            ?.also { log.warn("[RATE_LIMIT] email verification resend IP limit exceeded ip={} retryAfter={}s", ip, it) }
            ?.let { throw RateLimitException(it) }

        rateLimiter.retryAfterIfExceeded("RATE:EMAIL_VERIFY_RESEND:EMAIL:$email", ttlSeconds = 900, limit = 3)
            ?.also {
                log.warn(
                    "[RATE_LIMIT] email verification resend email limit exceeded email={} retryAfter={}s",
                    maskEmail(email),
                    it,
                )
            }
            ?.let { throw RateLimitException(it) }
    }
}
