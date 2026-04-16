package io.github.chan808.authtemplate.auth.infrastructure.redis

import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

@Component
class RefreshTokenStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : TokenStore {

    companion object {
        private const val RT_PREFIX = "RT:"
        private const val LOCK_PREFIX = "LOCK:REISSUE:"
        private const val LOCK_TTL = 3L
        private const val MEMBER_SESSIONS_PREFIX = "MEMBER_SESSIONS:"
        private const val MEMBER_SESSIONS_TTL = 30L * 24 * 3600
        private const val ACCESS_TOKEN_VERSION_PREFIX = "ATV:"

        // ATV 캐시는 AT 검증 시 DB 조회를 줄이는 것만이 목적이다.
        // 비밀번호 변경/탈퇴 이벤트에서 캐시 무효화가 실패했을 경우 stale version이 허용되는
        // 최대 시간이 이 TTL이므로, AT 자체 수명(약 30분)에 가깝게 1시간으로 제한한다.
        // 캐시 만료 후에는 DB에서 다시 읽어 재캐시되며 기능상 영향은 없다.
        private const val ACCESS_TOKEN_VERSION_TTL = 3600L

        private val DELETE_ALL_SESSIONS_SCRIPT = DefaultRedisScript(
            """
            local setKey = KEYS[1]
            local rtPrefix = ARGV[1]
            local sids = redis.call('SMEMBERS', setKey)
            for _, sid in ipairs(sids) do
                redis.call('DEL', rtPrefix .. sid)
            end
            redis.call('DEL', setKey)
            return #sids
            """.trimIndent(),
            Long::class.java,
        )

        private val DELETE_SESSION_SCRIPT = DefaultRedisScript(
            """
            local rtKey = KEYS[1]
            local setKey = KEYS[2]
            local sid = ARGV[1]
            redis.call('DEL', rtKey)
            redis.call('SREM', setKey, sid)
            if redis.call('SCARD', setKey) == 0 then
                redis.call('DEL', setKey)
            end
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }

    override fun save(sid: String, session: RefreshTokenSession, ttlSeconds: Long) {
        redisTemplate.opsForValue().set(
            "$RT_PREFIX$sid",
            objectMapper.writeValueAsString(session),
            ttlSeconds,
            TimeUnit.SECONDS,
        )
    }

    override fun find(sid: String): RefreshTokenSession? =
        redisTemplate.opsForValue().get("$RT_PREFIX$sid")
            ?.let { objectMapper.readValue(it, RefreshTokenSession::class.java) }

    override fun deleteSession(memberId: Long, sid: String) {
        redisTemplate.execute(
            DELETE_SESSION_SCRIPT,
            listOf("$RT_PREFIX$sid", "$MEMBER_SESSIONS_PREFIX$memberId"),
            sid,
        )
    }

    override fun tryLock(sid: String): Boolean =
        redisTemplate.opsForValue().setIfAbsent("$LOCK_PREFIX$sid", "1", LOCK_TTL, TimeUnit.SECONDS) ?: false

    override fun releaseLock(sid: String) {
        redisTemplate.delete("$LOCK_PREFIX$sid")
    }

    override fun addSession(memberId: Long, sid: String) {
        val key = "$MEMBER_SESSIONS_PREFIX$memberId"
        redisTemplate.opsForSet().add(key, sid)
        redisTemplate.expire(key, MEMBER_SESSIONS_TTL, TimeUnit.SECONDS)
    }

    override fun deleteAllSessionsForMember(memberId: Long) {
        val setKey = "$MEMBER_SESSIONS_PREFIX$memberId"
        redisTemplate.execute(DELETE_ALL_SESSIONS_SCRIPT, listOf(setKey), RT_PREFIX)
    }

    override fun findAccessTokenVersion(memberId: Long): Long? =
        redisTemplate.opsForValue().get("$ACCESS_TOKEN_VERSION_PREFIX$memberId")?.toLongOrNull()

    override fun cacheAccessTokenVersion(memberId: Long, tokenVersion: Long) {
        redisTemplate.opsForValue().set(
            "$ACCESS_TOKEN_VERSION_PREFIX$memberId",
            tokenVersion.toString(),
            ACCESS_TOKEN_VERSION_TTL,
            TimeUnit.SECONDS,
        )
    }

    override fun deleteAccessTokenVersion(memberId: Long) {
        redisTemplate.delete("$ACCESS_TOKEN_VERSION_PREFIX$memberId")
    }
}
