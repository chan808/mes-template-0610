package io.github.chan808.authtemplate.member.presentation

import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    // null이면 닉네임 제거, 문자열이면 설정 (빈 문자열은 서비스에서 null로 처리)
    @field:Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    val nickname: String?,
)
