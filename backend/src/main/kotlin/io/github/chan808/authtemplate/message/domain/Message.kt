package io.github.chan808.authtemplate.message.domain

import io.github.chan808.authtemplate.message.api.MessageType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import jakarta.persistence.EntityListeners
import java.time.LocalDateTime

// messages는 수정/삭제 없이 append-only이므로 createdAt만 필요
@Entity
@Table(name = "messages")
@EntityListeners(AuditingEntityListener::class)
class Message(
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Column(name = "user_id", nullable = true)
    val userId: Long?,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: MessageType = MessageType.chat,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
}
