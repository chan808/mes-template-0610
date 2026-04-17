package io.github.chan808.agolive.room.infrastructure.persistence

import io.github.chan808.agolive.room.domain.Room
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime
import java.util.UUID

interface RoomRepository : JpaRepository<Room, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Room?
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Room>
    fun findByInviteTokenAndDeletedAtIsNull(token: UUID): Room?

    @Modifying
    @Query("DELETE FROM Room r WHERE r.deletedAt < :cutoff")
    fun deleteByDeletedAtBefore(@Param("cutoff") cutoff: OffsetDateTime)
}
