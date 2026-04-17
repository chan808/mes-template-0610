package io.github.chan808.authtemplate.auth.presentation

import jakarta.validation.constraints.NotBlank

data class VerifyEmailRequest(
    @field:NotBlank
    val token: String,
)
