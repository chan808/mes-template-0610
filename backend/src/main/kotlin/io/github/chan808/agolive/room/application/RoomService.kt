package io.github.chan808.agolive.room.application

import io.github.chan808.agolive.common.ErrorCode
import io.github.chan808.agolive.common.RoomException
import io.github.chan808.agolive.room.api.RoomApi
import io.github.chan808.agolive.room.domain.Room
import io.github.chan808.agolive.room.infrastructure.persistence.RoomRepository
import io.github.chan808.agolive.room.presentation.CreateRoomRequest
import io.github.chan808.agolive.room.presentation.InviteTokenResponse
import io.github.chan808.agolive.room.presentation.RoomResponse
import io.github.chan808.agolive.room.presentation.UpdateRoomRequest
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class RoomService(private val roomRepository: RoomRepository) : RoomApi {

    private val log = LoggerFactory.getLogger(RoomService::class.java)

    @Transactional
    fun create(userId: Long, request: CreateRoomRequest): RoomResponse {
        val room = roomRepository.save(
            Room(name = request.name, ownerId = userId, isPrivate = request.isPrivate, maxCapacity = request.maxCapacity),
        )
        log.info("[ROOM] created roomId={} ownerId={}", room.id, userId)
        return RoomResponse.from(room, includeInviteToken = true)
    }

    fun list(pageable: Pageable): Page<RoomResponse> =
        roomRepository.findAllByDeletedAtIsNull(pageable).map { RoomResponse.from(it) }

    fun get(roomId: Long): RoomResponse = RoomResponse.from(require(roomId))

    fun getByInviteToken(token: String): RoomResponse {
        val uuid = runCatching { UUID.fromString(token) }
            .getOrElse { throw RoomException(ErrorCode.INVALID_INVITE_TOKEN) }
        val room = roomRepository.findByInviteTokenAndDeletedAtIsNull(uuid)
            ?: throw RoomException(ErrorCode.INVALID_INVITE_TOKEN)
        return RoomResponse.from(room)
    }

    @Transactional
    fun update(userId: Long, roomId: Long, request: UpdateRoomRequest): RoomResponse {
        val room = requireOwner(userId, roomId)
        room.update(request.name, request.isPrivate, request.maxCapacity)
        log.info("[ROOM] updated roomId={} userId={}", roomId, userId)
        return RoomResponse.from(room)
    }

    @Transactional
    fun delete(userId: Long, roomId: Long) {
        val room = requireOwner(userId, roomId)
        room.softDelete()
        log.info("[ROOM] deleted roomId={} userId={}", roomId, userId)
    }

    @Transactional
    fun regenerateInviteToken(userId: Long, roomId: Long): InviteTokenResponse {
        val room = requireOwner(userId, roomId)
        val newToken = room.regenerateInviteToken()
        log.info("[ROOM] invite token regenerated roomId={} userId={}", roomId, userId)
        return InviteTokenResponse(newToken)
    }

    override fun existsActiveRoom(roomId: Long): Boolean =
        roomRepository.findByIdAndDeletedAtIsNull(roomId) != null

    private fun require(roomId: Long): Room =
        roomRepository.findByIdAndDeletedAtIsNull(roomId) ?: throw RoomException(ErrorCode.ROOM_NOT_FOUND)

    private fun requireOwner(userId: Long, roomId: Long): Room {
        val room = require(roomId)
        if (room.ownerId != userId) throw RoomException(ErrorCode.ACCESS_DENIED)
        return room
    }
}
