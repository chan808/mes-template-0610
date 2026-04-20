package io.github.chan808.agolive.internal.presentation

import io.github.chan808.agolive.common.ApiResponse
import io.github.chan808.agolive.user.api.UserApi
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// Go 실시간 서버 전용 — Nginx에서 외부 접근 차단
@RestController
@RequestMapping("/internal")
class InternalAuthController(private val userApi: UserApi) {

    @PostMapping("/auth/verify")
    fun verify(@AuthenticationPrincipal userId: Long): ResponseEntity<ApiResponse<VerifyTokenResponse>> {
        val user = userApi.findAuthUserById(userId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ApiResponse.of(VerifyTokenResponse(user.id, user.nickname, user.avatarId)))
    }
}
