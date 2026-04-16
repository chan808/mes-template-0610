package io.github.chan808.authtemplate.member.presentation

import io.github.chan808.authtemplate.member.domain.Member
import java.time.LocalDateTime

data class MemberResponse(
    val id: Long,
    val email: String,
    val nickname: String?,
    // null이면 로컬 계정, 값이 있으면 소셜 계정 (GOOGLE/NAVER/KAKAO)
    val provider: String?,
    val role: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(member: Member): MemberResponse = MemberResponse(
            id = member.id,
            email = member.email,
            nickname = member.nickname,
            provider = member.provider,
            role = member.role.name,
            createdAt = member.createdAt,
        )
    }
}
