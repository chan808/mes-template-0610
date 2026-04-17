package io.github.chan808.agolive.room.domain

import io.github.chan808.agolive.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Entity
@Table(name = "rooms")
class Room(
    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,

    @Column(name = "invite_token", nullable = false, unique = true, columnDefinition = "uuid")
    var inviteToken: UUID = UUID.randomUUID(),

    @Column(name = "is_private", nullable = false)
    var isPrivate: Boolean = false,

    @Column(name = "max_capacity", nullable = false)
    var maxCapacity: Int = 10,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RoomStatus = RoomStatus.active,

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) : BaseEntity() {

    fun update(name: String?, isPrivate: Boolean?, maxCapacity: Int?) {
        name?.trim()?.ifBlank { null }?.let { this.name = it }
        isPrivate?.let { this.isPrivate = it }
        maxCapacity?.let { this.maxCapacity = it }
    }

    fun regenerateInviteToken(): UUID {
        inviteToken = UUID.randomUUID()
        return inviteToken
    }

    fun softDelete() {
        deletedAt = OffsetDateTime.now(ZoneOffset.UTC)
        status = RoomStatus.closed
    }
}
