package io.github.chan808.authtemplate.auth.infrastructure.redis

import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import io.github.chan808.authtemplate.auth.infrastructure.redis.RefreshTokenStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers
class RefreshTokenStoreTest {

    companion object {
        @Container
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:8-alpine")
            .withExposedPorts(6379)
    }

    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var store: RefreshTokenStore

    private val session = RefreshTokenSession(
        memberId = 1L,
        role = "USER",
        tokenHash = "hash-value",
        absoluteExpiryEpoch = Instant.now().plusSeconds(2592000).epochSecond,
    )

    @BeforeEach
    fun setup() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
        connectionFactory.afterPropertiesSet()

        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()

        store = RefreshTokenStore(redisTemplate, ObjectMapper())
    }

    @AfterEach
    fun cleanup() {
        redisTemplate.connectionFactory?.connection?.flushAll()
        connectionFactory.destroy()
    }

    @Test
    fun `save then find returns same session`() {
        val sid = UUID.randomUUID().toString()

        store.save(sid, session, ttlSeconds = 3600)

        val found = store.find(sid)
        assertNotNull(found)
        assertEquals(1L, found.memberId)
        assertEquals("USER", found.role)
        assertEquals("hash-value", found.tokenHash)
    }

    @Test
    fun `find on missing sid returns null`() {
        assertNull(store.find("missing"))
    }

    @Test
    fun `delete session removes token and member index entry`() {
        val sid = UUID.randomUUID().toString()
        store.save(sid, session, ttlSeconds = 3600)
        store.addSession(1L, sid)

        store.deleteSession(1L, sid)

        assertNull(store.find(sid))
        assertFalse(redisTemplate.opsForSet().isMember("MEMBER_SESSIONS:1", sid) ?: false)
    }

    @Test
    fun `save applies ttl`() {
        val sid = UUID.randomUUID().toString()
        store.save(sid, session, ttlSeconds = 100)

        val ttl = redisTemplate.getExpire("RT:$sid")
        assertTrue(ttl in 98..100)
    }

    @Test
    fun `try lock is exclusive for same sid`() {
        val sid = UUID.randomUUID().toString()

        assertTrue(store.tryLock(sid))
        assertFalse(store.tryLock(sid))
    }

    @Test
    fun `release lock allows locking again`() {
        val sid = UUID.randomUUID().toString()
        store.tryLock(sid)

        store.releaseLock(sid)

        assertTrue(store.tryLock(sid))
    }

    @Test
    fun `delete all sessions for member removes keys and set`() {
        val sid = UUID.randomUUID().toString()
        store.save(sid, session, ttlSeconds = 3600)
        store.addSession(1L, sid)

        store.deleteAllSessionsForMember(1L)

        assertNull(store.find(sid))
        assertFalse(redisTemplate.hasKey("MEMBER_SESSIONS:1"))
    }

    @Test
    fun `delete all sessions removes multiple sids atomically`() {
        val sids = List(3) { UUID.randomUUID().toString() }
        sids.forEach { sid ->
            store.save(sid, session, ttlSeconds = 3600)
            store.addSession(1L, sid)
        }

        store.deleteAllSessionsForMember(1L)

        sids.forEach { sid -> assertNull(store.find(sid)) }
        assertFalse(redisTemplate.hasKey("MEMBER_SESSIONS:1"))
    }

    @Test
    fun `delete all sessions on empty member does nothing`() {
        store.deleteAllSessionsForMember(99L)
    }

    @Test
    fun `access token version can be cached and read back`() {
        store.cacheAccessTokenVersion(1L, 7L)

        assertEquals(7L, store.findAccessTokenVersion(1L))
    }

    @Test
    fun `deleting access token version removes cached value`() {
        store.cacheAccessTokenVersion(1L, 7L)

        store.deleteAccessTokenVersion(1L)

        assertNull(store.findAccessTokenVersion(1L))
    }
}
