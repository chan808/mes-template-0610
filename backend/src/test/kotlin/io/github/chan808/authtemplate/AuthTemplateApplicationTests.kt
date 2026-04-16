package io.github.chan808.authtemplate

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("integration")
@SpringBootTest
@Testcontainers
class AuthTemplateApplicationTests {

    companion object {
        @Container
        @JvmField
        val mysql = MySQLContainer("mysql:8.0")
            .withDatabaseName("auth_template_test")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:8-alpine")
            .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            registry.add("spring.data.redis.password") { "" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("jwt.secret") { "integration-test-jwt-secret-that-is-long-enough-for-hs256" }
        }
    }

    @Test
    fun contextLoads() {}
}
