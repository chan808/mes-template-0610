package io.github.chan808.authtemplate.auth.infrastructure.redis

import io.github.chan808.authtemplate.auth.application.port.PasswordResetTokenStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class PasswordResetStore(private val redisTemplate: StringRedisTemplate) : PasswordResetTokenStore {

    companion object {
        private const val PREFIX = "RESET:"
        private const val TTL_SECONDS = 1800L // 30분
    }

    override fun save(token: String, memberId: Long) {
        redisTemplate.opsForValue().set("$PREFIX$token", memberId.toString(), TTL_SECONDS, TimeUnit.SECONDS)
    }

    override fun consume(token: String): Long? =
        redisTemplate.opsForValue().getAndDelete("$PREFIX$token")?.toLongOrNull()
}
