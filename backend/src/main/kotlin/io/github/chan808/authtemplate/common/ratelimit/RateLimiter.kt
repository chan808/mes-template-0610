package io.github.chan808.authtemplate.common.ratelimit

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RateLimiter(private val redisTemplate: StringRedisTemplate) {

    private val script = DefaultRedisScript(
        """
        local windowMs = tonumber(ARGV[1]) * 1000
        local limit = tonumber(ARGV[2])
        local member = ARGV[3]
        local now = redis.call('TIME')
        local nowMs = (tonumber(now[1]) * 1000) + math.floor(tonumber(now[2]) / 1000)
        local cutoff = nowMs - windowMs

        redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, cutoff)
        redis.call('ZADD', KEYS[1], nowMs, member)
        redis.call('PEXPIRE', KEYS[1], windowMs)

        local count = redis.call('ZCARD', KEYS[1])
        if count <= limit then
            return 0
        end

        local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
        if oldest[2] == nil then
            return 1
        end

        local retryAfterMs = windowMs - (nowMs - tonumber(oldest[2]))
        if retryAfterMs < 1 then
            retryAfterMs = 1
        end

        return math.ceil(retryAfterMs / 1000)
        """.trimIndent(),
        Long::class.java,
    )

    fun retryAfterIfExceeded(key: String, ttlSeconds: Long, limit: Int): Long? {
        val retryAfter = redisTemplate.execute(
            script,
            listOf(key),
            ttlSeconds.toString(),
            limit.toString(),
            UUID.randomUUID().toString(),
        ) ?: 0L
        return retryAfter.takeIf { it > 0 }
    }
}
