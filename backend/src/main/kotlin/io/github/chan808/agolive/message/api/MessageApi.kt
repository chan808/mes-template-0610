package io.github.chan808.agolive.message.api

import java.time.OffsetDateTime

// internal 등 타 모듈에서 메시지 저장/조회 시 사용하는 공개 API
interface MessageApi {
    fun save(roomId: Long, userId: Long?, content: String, type: MessageType = MessageType.chat): MessageRecord
    fun getRecentMessages(roomId: Long, limit: Int): List<MessageRecord>
}

data class MessageRecord(
    val id: Long,
    val roomId: Long,
    val userId: Long?,
    val content: String,
    val type: MessageType,
    val createdAt: OffsetDateTime,
)
