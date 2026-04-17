package io.github.chan808.authtemplate.room.infrastructure.persistence

import io.github.chan808.authtemplate.room.domain.Room
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface RoomRepository : JpaRepository<Room, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Room?
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Room>
    fun findByInviteTokenAndDeletedAtIsNull(token: UUID): Room?
    fun findAllByDeletedAtBefore(cutoff: LocalDateTime): List<Room>
}
