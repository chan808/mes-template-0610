package io.github.chan808.agolive.user.infrastructure.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class EmailVerificationStore(private val redisTemplate: StringRedisTemplate) {

    companion object {
        private const val TOKEN_PREFIX = "EMAIL_VERIFY:"
        private const val USER_PREFIX = "EMAIL_VERIFY_USER:"
    }

    fun save(token: String, userId: Long, ttlSeconds: Long) {
        deleteByUserId(userId)
        redisTemplate.opsForValue().set("$TOKEN_PREFIX$token", userId.toString(), ttlSeconds, TimeUnit.SECONDS)
        redisTemplate.opsForValue().set("$USER_PREFIX$userId", token, ttlSeconds, TimeUnit.SECONDS)
    }

    fun findUserId(token: String): Long? =
        redisTemplate.opsForValue().get("$TOKEN_PREFIX$token")?.toLongOrNull()

    fun delete(token: String) {
        val userId = findUserId(token)
        redisTemplate.delete("$TOKEN_PREFIX$token")
        userId?.let { redisTemplate.delete("$USER_PREFIX$it") }
    }

    fun deleteByUserId(userId: Long) {
        val token = redisTemplate.opsForValue().get("$USER_PREFIX$userId")
        redisTemplate.delete("$USER_PREFIX$userId")
        token?.let { redisTemplate.delete("$TOKEN_PREFIX$it") }
    }
}
