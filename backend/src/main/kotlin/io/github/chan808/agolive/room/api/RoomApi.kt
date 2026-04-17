package io.github.chan808.authtemplate.room.api

// message 등 타 모듈에서 room 정보를 조회할 때 사용하는 공개 API
interface RoomApi {
    fun existsActiveRoom(roomId: Long): Boolean
}
