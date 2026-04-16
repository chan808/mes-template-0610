package io.github.chan808.authtemplate.common.ratelimit

import io.github.chan808.authtemplate.common.ratelimit.RateLimiter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers
class RateLimiterTest {

    companion object {
        @Container
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:8-alpine")
            .withExposedPorts(6379)
    }

    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var rateLimiter: RateLimiter

    @BeforeEach
    fun setup() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
        connectionFactory.afterPropertiesSet()

        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()

        rateLimiter = RateLimiter(redisTemplate)
    }

    @AfterEach
    fun cleanup() {
        redisTemplate.connectionFactory?.connection?.flushAll()
        connectionFactory.destroy()
    }

    @Test
    fun `under limit returns null`() {
        val result = rateLimiter.retryAfterIfExceeded(
            key = "RATE:TEST:user1",
            ttlSeconds = 60,
            limit = 5,
        )
        assertNull(result)
    }

    @Test
    fun `over limit returns retry after seconds`() {
        val key = "RATE:TEST:user2"

        repeat(2) { rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 60, limit = 2) }
        val retryAfter = rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 60, limit = 2)

        assertNotNull(retryAfter)
        assertTrue(retryAfter > 0)
    }

    @Test
    fun `request count increments correctly`() {
        val key = "RATE:TEST:user3"

        repeat(5) {
            assertNull(rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 60, limit = 5))
        }
        assertNotNull(rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 60, limit = 5))
    }

    @Test
    fun `first request sets ttl`() {
        val key = "RATE:TEST:user4"

        rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 100, limit = 10)

        val ttl = redisTemplate.getExpire(key)
        assertTrue(ttl > 0)
    }

    @Test
    fun `count resets after ttl expires`() {
        val key = "RATE:TEST:user5"

        repeat(3) { rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 1, limit = 2) }
        Thread.sleep(1200)

        assertNull(rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 1, limit = 2))
    }
}
