package io.github.chan808.agolive.message.presentation

import io.github.chan808.agolive.common.ApiResponse
import io.github.chan808.agolive.message.application.MessageService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/messages")
class MessageController(private val messageService: MessageService) {

    @GetMapping
    fun list(
        @PathVariable roomId: Long,
        @RequestParam(required = false) before: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<MessageCursorResponse>> {
        val safeLimit = limit.coerceIn(1, 100)
        return ResponseEntity.ok(ApiResponse.of(messageService.list(roomId, before, safeLimit, userId)))
    }
}
