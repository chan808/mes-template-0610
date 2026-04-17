package io.github.chan808.authtemplate.message.presentation

import io.github.chan808.authtemplate.message.api.MessageType
import io.github.chan808.authtemplate.message.domain.Message
import java.time.LocalDateTime

data class MessageResponse(
    val id: Long,
    val roomId: Long,
    val userId: Long?,
    val content: String,
    val type: MessageType,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(message: Message) = MessageResponse(
            id = message.id,
            roomId = message.roomId,
            userId = message.userId,
            content = message.content,
            type = message.type,
            createdAt = message.createdAt,
        )
    }
}

data class MessageCursorResponse(
    val messages: List<MessageResponse>,
    val hasMore: Boolean,
    val nextCursor: Long?,
)
