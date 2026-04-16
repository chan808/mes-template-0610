package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.common.RateLimitException
import io.github.chan808.authtemplate.common.ratelimit.RateLimiter
import io.github.chan808.authtemplate.common.maskEmail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PasswordResetRateLimitService(private val rateLimiter: RateLimiter) {

    private val log = LoggerFactory.getLogger(PasswordResetRateLimitService::class.java)

    fun check(ip: String, email: String) {
        rateLimiter.retryAfterIfExceeded("RATE:PASSWORD_RESET:IP:$ip", ttlSeconds = 3600, limit = 10)
            ?.also { log.warn("[RATE_LIMIT] password reset IP limit exceeded ip={} retryAfter={}s", ip, it) }
            ?.let { throw RateLimitException(it) }

        rateLimiter.retryAfterIfExceeded("RATE:PASSWORD_RESET:EMAIL:$email", ttlSeconds = 900, limit = 5)
            ?.also { log.warn("[RATE_LIMIT] password reset email limit exceeded email={} retryAfter={}s", maskEmail(email), it) }
            ?.let { throw RateLimitException(it) }
    }
}
