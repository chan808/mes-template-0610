package io.github.chan808.authtemplate.room.presentation

import io.github.chan808.authtemplate.common.ApiResponse
import io.github.chan808.authtemplate.common.PageResponse
import io.github.chan808.authtemplate.room.application.RoomService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rooms")
class RoomController(private val roomService: RoomService) {

    @PostMapping
    fun create(
        @RequestBody @Valid request: CreateRoomRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<RoomResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(roomService.create(userId, request)))

    @GetMapping
    fun list(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<ApiResponse<PageResponse<RoomResponse>>> =
        ResponseEntity.ok(ApiResponse.of(PageResponse.from(roomService.list(pageable))))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<ApiResponse<RoomResponse>> =
        ResponseEntity.ok(ApiResponse.of(roomService.get(id)))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateRoomRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<RoomResponse>> =
        ResponseEntity.ok(ApiResponse.of(roomService.update(userId, id, request)))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<Unit>> {
        roomService.delete(userId, id)
        return ResponseEntity.ok(ApiResponse.success())
    }

    // 초대 토큰 재생성
    @PostMapping("/{id}/invite")
    fun regenerateInviteToken(
        @PathVariable id: Long,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<InviteTokenResponse>> =
        ResponseEntity.ok(ApiResponse.of(roomService.regenerateInviteToken(userId, id)))

    // 초대 링크로 방 정보 조회
    @GetMapping("/join/{token}")
    fun getByInviteToken(@PathVariable token: String): ResponseEntity<ApiResponse<RoomResponse>> =
        ResponseEntity.ok(ApiResponse.of(roomService.getByInviteToken(token)))
}
