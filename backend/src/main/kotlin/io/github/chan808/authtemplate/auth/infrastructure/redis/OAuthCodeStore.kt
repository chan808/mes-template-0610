package io.github.chan808.authtemplate.auth.infrastructure.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * OAuth 로그인 후 AT를 프론트엔드에 안전하게 전달하기 위한 one-time code 저장소
 * URL에 AT를 직접 노출하지 않음: OAUTH_CODE:{code} → AT (TTL 60초)
 */
@Component
class OAuthCodeStore(private val redisTemplate: StringRedisTemplate) {

    companion object {
        private const val PREFIX = "OAUTH_CODE:"
        private const val TTL_SECONDS = 60L
    }

    fun save(code: String, accessToken: String) {
        redisTemplate.opsForValue().set("$PREFIX$code", accessToken, TTL_SECONDS, TimeUnit.SECONDS)
    }

    /** 조회 즉시 삭제 — one-time 보장 */
    fun findAndDelete(code: String): String? {
        val key = "$PREFIX$code"
        return redisTemplate.opsForValue().getAndDelete(key)
    }
}
