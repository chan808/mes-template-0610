package io.github.chan808.agolive.message.application

import io.github.chan808.agolive.common.ErrorCode
import io.github.chan808.agolive.common.RoomException
import io.github.chan808.agolive.message.api.MessageApi
import io.github.chan808.agolive.message.api.MessageRecord
import io.github.chan808.agolive.message.api.MessageType
import io.github.chan808.agolive.message.domain.Message
import io.github.chan808.agolive.message.infrastructure.persistence.MessageRepository
import io.github.chan808.agolive.message.presentation.MessageCursorResponse
import io.github.chan808.agolive.message.presentation.MessageResponse
import io.github.chan808.agolive.room.api.RoomApi
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MessageService(
    private val messageRepository: MessageRepository,
    private val roomApi: RoomApi,
) : MessageApi {

    fun list(roomId: Long, beforeId: Long?, limit: Int, userId: Long): MessageCursorResponse {
        val room = roomApi.getActiveRoomInfo(roomId) ?: throw RoomException(ErrorCode.ROOM_NOT_FOUND)
        if (room.isPrivate && room.ownerId != userId) throw RoomException(ErrorCode.ACCESS_DENIED)
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

    override fun getRecentMessages(roomId: Long, limit: Int): List<MessageRecord> =
        messageRepository.findByRoomIdOrderByIdDesc(roomId, PageRequest.of(0, limit))
            .reversed()
            .map { MessageRecord(it.id, it.roomId, it.userId, it.content, it.type, it.createdAt, it.agentNickname) }

    @Transactional
    override fun save(
        roomId: Long,
        userId: Long?,
        content: String,
        type: MessageType,
        agentNickname: String?,
    ): MessageRecord {
        val message = messageRepository.save(
            Message(roomId = roomId, userId = userId, content = content, type = type, agentNickname = agentNickname),
        )
        return MessageRecord(
            id = message.id,
            roomId = message.roomId,
            userId = message.userId,
            content = message.content,
            type = message.type,
            createdAt = message.createdAt,
            agentNickname = message.agentNickname,
        )
    }
}
