package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.common.RateLimitException
import io.github.chan808.authtemplate.common.ratelimit.RateLimiter
import io.github.chan808.authtemplate.common.maskEmail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LoginRateLimitService(private val rateLimiter: RateLimiter) {

    private val log = LoggerFactory.getLogger(LoginRateLimitService::class.java)

    fun check(ip: String, email: String) {
        // IP 기준: 사무실/공유망 등 다수 사용자를 감안해 한도를 높게 설정 (password spraying 탐지용)
        rateLimiter.retryAfterIfExceeded("RATE:LOGIN:IP:$ip", ttlSeconds = 3600, limit = 20)
            ?.also { log.warn("[RATE_LIMIT] 로그인 IP 한도 초과 ip={} retryAfter={}s", ip, it) }
            ?.let { throw RateLimitException(it) }

        // 이메일 기준: 한 계정에 집중되는 분산 IP 공격(credential stuffing) 탐지용
        rateLimiter.retryAfterIfExceeded("RATE:LOGIN:EMAIL:$email", ttlSeconds = 900, limit = 10)
            ?.also { log.warn("[RATE_LIMIT] 로그인 이메일 한도 초과 email={} retryAfter={}s", maskEmail(email), it) }
            ?.let { throw RateLimitException(it) }
    }
}
