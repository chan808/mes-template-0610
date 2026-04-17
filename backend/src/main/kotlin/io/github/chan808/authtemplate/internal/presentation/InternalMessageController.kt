package io.github.chan808.authtemplate.internal.presentation

import io.github.chan808.authtemplate.common.ApiResponse
import io.github.chan808.authtemplate.message.api.MessageApi
import io.github.chan808.authtemplate.message.api.MessageRecord
import io.github.chan808.authtemplate.message.api.MessageType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// Go 실시간 서버 전용 — Nginx에서 외부 접근 차단
@RestController
@RequestMapping("/internal/rooms/{roomId}/messages")
class InternalMessageController(private val messageApi: MessageApi) {

    @PostMapping
    fun save(
        @PathVariable roomId: Long,
        @RequestBody @Valid request: SaveMessageRequest,
    ): ResponseEntity<ApiResponse<MessageRecord>> {
        val record = messageApi.save(roomId, request.userId, request.content, request.type)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(record))
    }
}

data class SaveMessageRequest(
    val userId: Long?,
    @field:NotBlank @field:Size(max = 2000)
    val content: String,
    val type: MessageType = MessageType.chat,
)
