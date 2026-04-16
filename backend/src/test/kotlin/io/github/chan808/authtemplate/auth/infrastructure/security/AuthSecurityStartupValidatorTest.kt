package io.github.chan808.authtemplate.auth.infrastructure.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertTrue

class AuthSecurityStartupValidatorTest {

    @Test
    fun `non prod profile allows local auth settings`() {
        val environment = MockEnvironment().apply { setActiveProfiles("local") }
        val validator = AuthSecurityStartupValidator(
            environment = environment,
            cookieSecure = false,
            appBaseUrl = "http://localhost:3000",
            corsAllowedOrigin = "http://localhost:3000",
        )

        assertDoesNotThrow { validator.afterPropertiesSet() }
    }

    @Test
    fun `prod profile rejects insecure cookie`() {
        val validator = validator(
            cookieSecure = false,
            appBaseUrl = "https://app.example.com",
            corsAllowedOrigin = "https://app.example.com",
        )

        val ex = assertThrows<IllegalStateException> { validator.afterPropertiesSet() }

        assertTrue(ex.message!!.contains("cookie.secure"))
    }

    @Test
    fun `prod profile rejects non https base url`() {
        val validator = validator(
            cookieSecure = true,
            appBaseUrl = "http://app.example.com",
            corsAllowedOrigin = "https://app.example.com",
        )

        val ex = assertThrows<IllegalStateException> { validator.afterPropertiesSet() }

        assertTrue(ex.message!!.contains("app.base-url"))
    }

    @Test
    fun `prod profile rejects loopback cors origin`() {
        val validator = validator(
            cookieSecure = true,
            appBaseUrl = "https://app.example.com",
            corsAllowedOrigin = "https://localhost:3000",
        )

        val ex = assertThrows<IllegalStateException> { validator.afterPropertiesSet() }

        assertTrue(ex.message!!.contains("cors.allowed-origin"))
    }

    @Test
    fun `prod profile accepts https public urls`() {
        val validator = validator(
            cookieSecure = true,
            appBaseUrl = "https://app.example.com",
            corsAllowedOrigin = "https://app.example.com",
        )

        assertDoesNotThrow { validator.afterPropertiesSet() }
    }

    private fun validator(
        cookieSecure: Boolean,
        appBaseUrl: String,
        corsAllowedOrigin: String,
    ) = AuthSecurityStartupValidator(
        environment = MockEnvironment().apply { setActiveProfiles("prod") },
        cookieSecure = cookieSecure,
        appBaseUrl = appBaseUrl,
        corsAllowedOrigin = corsAllowedOrigin,
    )
}
