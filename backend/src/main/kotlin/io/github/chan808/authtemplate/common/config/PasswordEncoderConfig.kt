package io.github.chan808.authtemplate.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class PasswordEncoderConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        val encoders = linkedMapOf<String, PasswordEncoder>(
            "bcrypt" to BCryptPasswordEncoder(12),
            "argon2" to Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
        )

        return DelegatingPasswordEncoder("bcrypt", encoders).apply {
            // Preserve compatibility with existing raw bcrypt hashes that predate the {id} prefix.
            setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder())
        }
    }
}
