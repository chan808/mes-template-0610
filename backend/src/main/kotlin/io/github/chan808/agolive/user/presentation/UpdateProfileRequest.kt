package io.github.chan808.agolive.user.presentation

import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    val nickname: String?,
    val avatarId: Long? = null,
)
