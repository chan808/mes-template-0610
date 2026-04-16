package io.github.chan808.authtemplate.common

import io.github.chan808.authtemplate.auth.presentation.PasswordResetConfirmRequest
import io.github.chan808.authtemplate.member.presentation.ChangePasswordRequest
import io.github.chan808.authtemplate.member.presentation.SignupRequest
import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationPolicyTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `signup password accepts unicode passphrase`() {
        val violations = validator.validate(
            SignupRequest(
                email = "test@example.com",
                password = "안전한 패스프레이즈 2026 edition",
            ),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `change password rejects too short password`() {
        val violations = validator.validate(
            ChangePasswordRequest(
                currentPassword = "current-password",
                newPassword = "짧다12",
            ),
        )

        assertFalse(violations.isEmpty())
    }

    @Test
    fun `password reset accepts spaces and symbols without composition rules`() {
        val violations = validator.validate(
            PasswordResetConfirmRequest(
                token = "token-123",
                newPassword = "correct horse battery staple !!!",
            ),
        )

        assertTrue(violations.isEmpty())
    }
}
