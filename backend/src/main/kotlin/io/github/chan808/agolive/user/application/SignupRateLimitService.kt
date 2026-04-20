package io.github.chan808.agolive.user.application

import io.github.chan808.agolive.common.RateLimitException
import io.github.chan808.agolive.common.ratelimit.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SignupRateLimitService(private val rateLimiter: RateLimiter) {

    private val log = LoggerFactory.getLogger(SignupRateLimitService::class.java)

    fun check(ip: String) {
        rateLimiter.retryAfterIfExceeded("RATE:SIGNUP:IP:$ip", ttlSeconds = 3600, limit = 5)
            ?.also { log.warn("[RATE_LIMIT] signup IP limit exceeded ip={} retryAfter={}s", ip, it) }
            ?.let { throw RateLimitException(it) }
    }
}
