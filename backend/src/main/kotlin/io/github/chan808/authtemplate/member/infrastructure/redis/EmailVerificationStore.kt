package io.github.chan808.authtemplate.member.infrastructure.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class EmailVerificationStore(private val redisTemplate: StringRedisTemplate) {

    companion object {
        private const val TOKEN_PREFIX = "EMAIL_VERIFY:"
        private const val MEMBER_PREFIX = "EMAIL_VERIFY_MEMBER:"
    }

    fun save(token: String, memberId: Long, ttlSeconds: Long) {
        deleteByMemberId(memberId)
        redisTemplate.opsForValue().set("$TOKEN_PREFIX$token", memberId.toString(), ttlSeconds, TimeUnit.SECONDS)
        redisTemplate.opsForValue().set("$MEMBER_PREFIX$memberId", token, ttlSeconds, TimeUnit.SECONDS)
    }

    fun findMemberId(token: String): Long? =
        redisTemplate.opsForValue().get("$TOKEN_PREFIX$token")?.toLongOrNull()

    fun delete(token: String) {
        val memberId = findMemberId(token)
        redisTemplate.delete("$TOKEN_PREFIX$token")
        memberId?.let { redisTemplate.delete("$MEMBER_PREFIX$it") }
    }

    fun deleteByMemberId(memberId: Long) {
        val token = redisTemplate.opsForValue().get("$MEMBER_PREFIX$memberId")
        redisTemplate.delete("$MEMBER_PREFIX$memberId")
        token?.let { redisTemplate.delete("$TOKEN_PREFIX$it") }
    }
}
