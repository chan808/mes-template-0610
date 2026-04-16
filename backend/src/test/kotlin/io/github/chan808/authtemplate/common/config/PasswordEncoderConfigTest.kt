package io.github.chan808.authtemplate.common.config

import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordEncoderConfigTest {

    private val passwordEncoder = PasswordEncoderConfig().passwordEncoder()

    @Test
    fun `new hashes are stored with an algorithm prefix`() {
        val encoded = passwordEncoder.encode("Password1!") ?: error("PasswordEncoder returned null")

        assertTrue(encoded.startsWith("{bcrypt}"))
        assertTrue(passwordEncoder.matches("Password1!", encoded))
        assertFalse(passwordEncoder.matches("WrongPassword1!", encoded))
    }

    @Test
    fun `legacy raw bcrypt hashes remain readable`() {
        val legacyHash = BCryptPasswordEncoder().encode("Password1!")

        assertTrue(passwordEncoder.matches("Password1!", legacyHash))
        assertFalse(passwordEncoder.matches("WrongPassword1!", legacyHash))
    }
}
