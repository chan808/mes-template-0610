package io.github.chan808.authtemplate.user.presentation

import io.github.chan808.authtemplate.common.ApiResponse
import io.github.chan808.authtemplate.common.ClientIpResolver
import io.github.chan808.authtemplate.user.application.UserCommandService
import io.github.chan808.authtemplate.user.infrastructure.persistence.AvatarTemplateRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userCommandService: UserCommandService,
    private val avatarTemplateRepository: AvatarTemplateRepository,
    private val clientIpResolver: ClientIpResolver,
) {

    // 회원가입
    @PostMapping("/api/users")
    fun signup(
        @RequestBody @Valid request: SignupRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        userCommandService.signup(request, clientIpResolver.resolve(servletRequest))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success())
    }

    // 내 프로필 조회 (agolive API)
    @GetMapping("/api/v1/users/me")
    fun getMyProfile(@AuthenticationPrincipal userId: Long): ResponseEntity<ApiResponse<UserProfileResponse>> =
        ResponseEntity.ok(ApiResponse.of(userCommandService.getMyProfile(userId)))

    // 프로필 수정 (닉네임, 아바타)
    @PatchMapping("/api/v1/users/me")
    fun updateProfile(
        @RequestBody @Valid request: UpdateProfileRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<UserProfileResponse>> =
        ResponseEntity.ok(ApiResponse.of(userCommandService.updateProfile(userId, request)))

    // 비밀번호 변경
    @PatchMapping("/api/v1/users/me/password")
    fun changePassword(
        @RequestBody @Valid request: ChangePasswordRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<Unit>> {
        userCommandService.changePassword(userId, request)
        return ResponseEntity.ok(ApiResponse.success())
    }

    // 회원 탈퇴
    @DeleteMapping("/api/v1/users/me")
    fun withdraw(
        @AuthenticationPrincipal userId: Long,
        response: HttpServletResponse,
    ): ResponseEntity<ApiResponse<Unit>> {
        userCommandService.withdraw(userId)
        // RT 쿠키 즉시 만료
        val expiredCookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true).path("/api/auth").maxAge(0).sameSite("Strict").build()
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString())
        return ResponseEntity.ok(ApiResponse.success())
    }

    // 아바타 목록 조회
    @GetMapping("/api/v1/avatars")
    fun getAvatars(): ResponseEntity<ApiResponse<List<AvatarResponse>>> {
        val avatars = avatarTemplateRepository.findAllByIsActiveTrue()
            .map { AvatarResponse(it.id, it.name, it.imageUrl) }
        return ResponseEntity.ok(ApiResponse.of(avatars))
    }
}
