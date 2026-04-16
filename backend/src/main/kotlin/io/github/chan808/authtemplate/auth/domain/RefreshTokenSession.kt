package io.github.chan808.authtemplate.auth.domain

// Redis에 JSON으로 저장되는 세션 정보
// absoluteExpiryEpoch: epoch seconds로 저장해 Jackson datetime 직렬화 이슈 회피
data class RefreshTokenSession(
    val memberId: Long,
    val role: String,
    val tokenHash: String,
    val absoluteExpiryEpoch: Long,
)
