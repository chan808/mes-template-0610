package io.github.chan808.authtemplate.member.presentation

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank(message = "현재 비밀번호를 입력해주세요.")
    val currentPassword: String,

    @field:NotBlank(message = "새 비밀번호를 입력해주세요.")
    @field:Size(
        min = 8,
        max = 128,
        message = "비밀번호는 8자 이상 128자 이하여야 합니다.",
    )
    val newPassword: String,
)
