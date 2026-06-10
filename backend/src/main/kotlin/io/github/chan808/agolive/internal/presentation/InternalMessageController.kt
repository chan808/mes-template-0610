package io.github.chan808.agolive.internal.presentation

import io.github.chan808.agolive.common.ApiResponse
import io.github.chan808.agolive.message.api.MessageApi
import io.github.chan808.agolive.message.api.MessageRecord
import io.github.chan808.agolive.message.api.MessageType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// Go 실시간 서버 / AI 에이전트 전용 — Nginx에서 외부 접근 차단
@RestController
@RequestMapping("/internal/rooms/{roomId}/messages")
class InternalMessageController(private val messageApi: MessageApi) {

    @PostMapping
    fun save(
        @PathVariable roomId: Long,
        @RequestBody @Valid request: SaveMessageRequest,
    ): ResponseEntity<ApiResponse<MessageRecord>> {
        val record = messageApi.save(roomId, request.userId, request.content, request.type, request.agentNickname)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(record))
    }

    @GetMapping("/context")
    fun getContext(
        @PathVariable roomId: Long,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<ApiResponse<List<MessageRecord>>> {
        val messages = messageApi.getRecentMessages(roomId, limit.coerceIn(1, 50))
        return ResponseEntity.ok(ApiResponse.of(messages))
    }
}

data class SaveMessageRequest(
    val userId: Long?,
    @field:NotBlank @field:Size(max = 4000)
    val content: String,
    val type: MessageType = MessageType.chat,
    // type=agent일 때 표시용 닉네임
    @field:Size(max = 50)
    val agentNickname: String? = null,
)
