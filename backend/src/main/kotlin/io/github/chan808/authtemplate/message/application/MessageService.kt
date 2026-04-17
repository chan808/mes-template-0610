package io.github.chan808.authtemplate.message.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.RoomException
import io.github.chan808.authtemplate.message.api.MessageApi
import io.github.chan808.authtemplate.message.api.MessageRecord
import io.github.chan808.authtemplate.message.api.MessageType
import io.github.chan808.authtemplate.message.domain.Message
import io.github.chan808.authtemplate.message.infrastructure.persistence.MessageRepository
import io.github.chan808.authtemplate.message.presentation.MessageCursorResponse
import io.github.chan808.authtemplate.message.presentation.MessageResponse
import io.github.chan808.authtemplate.room.api.RoomApi
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MessageService(
    private val messageRepository: MessageRepository,
    private val roomApi: RoomApi,
) : MessageApi {

    fun list(roomId: Long, beforeId: Long?, limit: Int): MessageCursorResponse {
        if (!roomApi.existsActiveRoom(roomId)) throw RoomException(ErrorCode.ROOM_NOT_FOUND)
        val pageRequest = PageRequest.of(0, limit + 1)
        val messages = if (beforeId != null) {
            messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, pageRequest)
        } else {
            messageRepository.findByRoomIdOrderByIdDesc(roomId, pageRequest)
        }
        val hasMore = messages.size > limit
        val result = if (hasMore) messages.dropLast(1) else messages
        return MessageCursorResponse(
            messages = result.map { MessageResponse.from(it) },
            hasMore = hasMore,
            nextCursor = if (hasMore) result.last().id else null,
        )
    }

    @Transactional
    override fun save(roomId: Long, userId: Long?, content: String, type: MessageType): MessageRecord {
        val message = messageRepository.save(Message(roomId = roomId, userId = userId, content = content, type = type))
        return MessageRecord(
            id = message.id,
            roomId = message.roomId,
            userId = message.userId,
            content = message.content,
            type = message.type,
            createdAt = message.createdAt,
        )
    }
}
