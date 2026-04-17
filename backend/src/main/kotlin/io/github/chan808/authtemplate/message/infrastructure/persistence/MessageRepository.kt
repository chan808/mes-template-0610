package io.github.chan808.authtemplate.message.infrastructure.persistence

import io.github.chan808.authtemplate.message.domain.Message
import org.springframework.data.jpa.repository.JpaRepository

interface MessageRepository : JpaRepository<Message, Long> {
    // cursor 기반 페이징: before(messageId) 미만의 메시지를 최신순으로 limit개 조회
    fun findByRoomIdAndIdLessThanOrderByIdDesc(roomId: Long, beforeId: Long, pageable: org.springframework.data.domain.Pageable): List<Message>
    fun findByRoomIdOrderByIdDesc(roomId: Long, pageable: org.springframework.data.domain.Pageable): List<Message>
}
