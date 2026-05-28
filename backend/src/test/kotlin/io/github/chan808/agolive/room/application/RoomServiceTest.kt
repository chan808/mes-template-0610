package io.github.chan808.agolive.room.application

import io.github.chan808.agolive.common.ErrorCode
import io.github.chan808.agolive.common.RoomException
import io.github.chan808.agolive.room.domain.Room
import io.github.chan808.agolive.room.infrastructure.persistence.RoomRepository
import io.github.chan808.agolive.room.presentation.CreateRoomRequest
import io.github.chan808.agolive.room.presentation.UpdateRoomRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomServiceTest {

    private val roomRepository: RoomRepository = mockk()
    private val redisTemplate: StringRedisTemplate = mockk()
    private val roomService = RoomService(roomRepository, redisTemplate)

    private fun room(id: Long = 1L, ownerId: Long = 10L) = Room(
        name = "테스트 방",
        ownerId = ownerId,
    ).apply {
        val field = Room::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(this, id)
    }

    @Test
    fun `방_생성_성공_초대토큰_포함`() {
        val request = CreateRoomRequest(name = "새 방", isPrivate = false, maxCapacity = 10)
        val saved = room(id = 1L, ownerId = 10L)
        every { roomRepository.save(any()) } returns saved

        val result = roomService.create(userId = 10L, request = request)

        assertNotNull(result.inviteToken)
        assertEquals(10L, result.ownerId)
    }

    @Test
    fun `소유자_아닌_유저가_수정시_ACCESS_DENIED`() {
        val existing = room(id = 1L, ownerId = 10L)
        every { roomRepository.findByIdAndDeletedAtIsNull(1L) } returns existing

        val ex = assertThrows<RoomException> {
            roomService.update(userId = 99L, roomId = 1L, request = UpdateRoomRequest(name = "변경"))
        }
        assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode)
    }

    @Test
    fun `소유자_아닌_유저가_삭제시_ACCESS_DENIED`() {
        val existing = room(id = 1L, ownerId = 10L)
        every { roomRepository.findByIdAndDeletedAtIsNull(1L) } returns existing

        val ex = assertThrows<RoomException> {
            roomService.delete(userId = 99L, roomId = 1L)
        }
        assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode)
    }

    @Test
    fun `소유자_아닌_유저가_초대토큰_재생성시_ACCESS_DENIED`() {
        val existing = room(id = 1L, ownerId = 10L)
        every { roomRepository.findByIdAndDeletedAtIsNull(1L) } returns existing

        val ex = assertThrows<RoomException> {
            roomService.regenerateInviteToken(userId = 99L, roomId = 1L)
        }
        assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode)
    }

    @Test
    fun `삭제된_방_조회시_ROOM_NOT_FOUND`() {
        every { roomRepository.findByIdAndDeletedAtIsNull(1L) } returns null

        val ex = assertThrows<RoomException> { roomService.get(1L) }
        assertEquals(ErrorCode.ROOM_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `유효하지_않은_초대토큰_형식_INVALID_INVITE_TOKEN`() {
        val ex = assertThrows<RoomException> { roomService.getByInviteToken("not-a-uuid") }
        assertEquals(ErrorCode.INVALID_INVITE_TOKEN, ex.errorCode)
    }

    @Test
    fun `존재하지_않는_초대토큰_INVALID_INVITE_TOKEN`() {
        every { roomRepository.findByInviteTokenAndDeletedAtIsNull(any()) } returns null

        val ex = assertThrows<RoomException> {
            roomService.getByInviteToken("550e8400-e29b-41d4-a716-446655440000")
        }
        assertEquals(ErrorCode.INVALID_INVITE_TOKEN, ex.errorCode)
    }

    @Test
    fun `방_삭제시_소프트_삭제_처리`() {
        val existing = room(id = 1L, ownerId = 10L)
        every { roomRepository.findByIdAndDeletedAtIsNull(1L) } returns existing

        roomService.delete(userId = 10L, roomId = 1L)

        assertNotNull(existing.deletedAt)
    }

    @Test
    fun `초대토큰_재생성시_새_토큰_반환`() {
        val existing = room(id = 1L, ownerId = 10L)
        val originalToken = existing.inviteToken
        every { roomRepository.findByIdAndDeletedAtIsNull(1L) } returns existing

        val result = roomService.regenerateInviteToken(userId = 10L, roomId = 1L)

        assertEquals(result.inviteToken, existing.inviteToken)
    }

    @Test
    fun `이름_공백만_있는_경우_업데이트_무시`() {
        val existing = room(id = 1L, ownerId = 10L)
        val originalName = existing.name
        every { roomRepository.findByIdAndDeletedAtIsNull(1L) } returns existing

        roomService.update(userId = 10L, roomId = 1L, request = UpdateRoomRequest(name = "   "))

        assertEquals(originalName, existing.name)
    }
}
