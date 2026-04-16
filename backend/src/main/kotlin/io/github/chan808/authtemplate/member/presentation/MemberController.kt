package io.github.chan808.authtemplate.member.presentation

import io.github.chan808.authtemplate.common.ApiResponse
import io.github.chan808.authtemplate.common.ClientIpResolver
import io.github.chan808.authtemplate.member.application.MemberCommandService
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberCommandService: MemberCommandService,
    private val clientIpResolver: ClientIpResolver,
) {

    @PostMapping
    fun signup(
        @RequestBody @Valid request: SignupRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        memberCommandService.signup(request, clientIpResolver.resolve(servletRequest))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success())
    }

    // principal = memberId(Long): JwtAuthenticationFilter에서 subject를 toLong()으로 설정
    @GetMapping("/me")
    fun getMyInfo(@AuthenticationPrincipal memberId: Long): ResponseEntity<ApiResponse<MemberResponse>> =
        ResponseEntity.ok(ApiResponse.of(memberCommandService.getMyInfo(memberId)))

    @PatchMapping("/me/profile")
    fun updateProfile(
        @RequestBody @Valid request: UpdateProfileRequest,
        @AuthenticationPrincipal memberId: Long,
    ): ResponseEntity<ApiResponse<MemberResponse>> =
        ResponseEntity.ok(ApiResponse.of(memberCommandService.updateProfile(memberId, request)))

    @PatchMapping("/me/password")
    fun changePassword(
        @RequestBody @Valid request: ChangePasswordRequest,
        @AuthenticationPrincipal memberId: Long,
    ): ResponseEntity<ApiResponse<Unit>> {
        memberCommandService.changePassword(memberId, request)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @DeleteMapping("/me")
    fun withdraw(
        @AuthenticationPrincipal memberId: Long,
        response: HttpServletResponse,
    ): ResponseEntity<ApiResponse<Unit>> {
        memberCommandService.withdraw(memberId)
        // RT 쿠키 즉시 만료 처리
        val expiredCookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .path("/api/auth")
            .maxAge(0)
            .sameSite("Strict")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString())
        return ResponseEntity.ok(ApiResponse.success())
    }
}
