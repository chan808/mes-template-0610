package io.github.chan808.authtemplate.room.presentation

import io.github.chan808.authtemplate.room.domain.Room
import io.github.chan808.authtemplate.room.domain.RoomStatus
import java.time.OffsetDateTime
import java.util.UUID

data class RoomResponse(
    val id: Long,
    val name: String,
    val ownerId: Long,
    val isPrivate: Boolean,
    val maxCapacity: Int,
    val status: RoomStatus,
    val inviteToken: UUID?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(room: Room, includeInviteToken: Boolean = false): RoomResponse = RoomResponse(
            id = room.id,
            name = room.name,
            ownerId = room.ownerId,
            isPrivate = room.isPrivate,
            maxCapacity = room.maxCapacity,
            status = room.status,
            inviteToken = if (includeInviteToken) room.inviteToken else null,
            createdAt = room.createdAt,
            updatedAt = room.updatedAt,
        )
    }
}

data class InviteTokenResponse(val inviteToken: UUID)
