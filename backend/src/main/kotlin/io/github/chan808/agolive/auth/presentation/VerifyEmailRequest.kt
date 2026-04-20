package io.github.chan808.agolive.auth.presentation

import jakarta.validation.constraints.NotBlank

data class VerifyEmailRequest(
    @field:NotBlank
    val token: String,
)
