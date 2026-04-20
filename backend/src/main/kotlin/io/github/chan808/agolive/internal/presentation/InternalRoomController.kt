package io.github.chan808.agolive.internal.presentation

import io.github.chan808.agolive.common.ApiResponse
import io.github.chan808.agolive.room.api.RoomApi
import io.github.chan808.agolive.room.api.RoomInfo
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// Go 실시간 서버 전용 — Nginx에서 외부 접근 차단
@RestController
@RequestMapping("/internal/rooms")
class InternalRoomController(private val roomApi: RoomApi) {

    @GetMapping("/{roomId}")
    fun getRoomInfo(@PathVariable roomId: Long): ResponseEntity<ApiResponse<RoomInfo>> {
        val info = roomApi.getActiveRoomInfo(roomId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ApiResponse.of(info))
    }
}
