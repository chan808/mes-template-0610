package io.github.chan808.agolive.room.api

// message, internal 등 타 모듈에서 room 정보를 조회할 때 사용하는 공개 API
interface RoomApi {
    fun existsActiveRoom(roomId: Long): Boolean
    fun getActiveRoomInfo(roomId: Long): RoomInfo?
}

data class RoomInfo(
    val id: Long,
    val maxCapacity: Int,
    val status: String,
)
