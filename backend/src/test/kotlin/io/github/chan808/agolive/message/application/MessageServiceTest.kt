package io.github.chan808.agolive.message.application

import io.github.chan808.agolive.common.ErrorCode
import io.github.chan808.agolive.common.RoomException
import io.github.chan808.agolive.message.api.MessageType
import io.github.chan808.agolive.message.domain.Message
import io.github.chan808.agolive.message.infrastructure.persistence.MessageRepository
import io.github.chan808.agolive.room.api.RoomApi
import io.github.chan808.agolive.room.api.RoomInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Pageable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageServiceTest {

    private val messageRepository: MessageRepository = mockk()
    private val roomApi: RoomApi = mockk()
    private val messageService = MessageService(messageRepository, roomApi)

    private fun message(id: Long, roomId: Long = 1L) = Message(
        roomId = roomId,
        userId = 10L,
        content = "메시지 $id",
        type = MessageType.chat,
    ).apply {
        val field = Message::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(this, id)
    }

    private fun publicRoom(roomId: Long = 1L) =
        RoomInfo(id = roomId, maxCapacity = 10, status = "active", isPrivate = false, ownerId = 99L)

    private fun privateRoom(ownerId: Long, roomId: Long = 1L) =
        RoomInfo(id = roomId, maxCapacity = 10, status = "active", isPrivate = true, ownerId = ownerId)

    @Test
    fun `존재하지_않는_방_메시지_조회시_ROOM_NOT_FOUND`() {
        every { roomApi.getActiveRoomInfo(999L) } returns null

        val ex = assertThrows<RoomException> {
            messageService.list(roomId = 999L, beforeId = null, limit = 50, userId = 1L)
        }
        assertEquals(ErrorCode.ROOM_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `private_방_오너가_아닌_사용자_접근시_ACCESS_DENIED`() {
        every { roomApi.getActiveRoomInfo(1L) } returns privateRoom(ownerId = 99L)

        val ex = assertThrows<RoomException> {
            messageService.list(roomId = 1L, beforeId = null, limit = 50, userId = 10L)
        }
        assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode)
    }

    @Test
    fun `private_방_오너는_메시지_조회_가능`() {
        every { roomApi.getActiveRoomInfo(1L) } returns privateRoom(ownerId = 99L)
        every { messageRepository.findByRoomIdOrderByIdDesc(1L, any<Pageable>()) } returns
            listOf(message(1L))

        val result = messageService.list(roomId = 1L, beforeId = null, limit = 50, userId = 99L)

        assertEquals(1, result.messages.size)
    }

    @Test
    fun `public_방은_누구든_메시지_조회_가능`() {
        every { roomApi.getActiveRoomInfo(1L) } returns publicRoom()
        every { messageRepository.findByRoomIdOrderByIdDesc(1L, any<Pageable>()) } returns
            listOf(message(1L))

        val result = messageService.list(roomId = 1L, beforeId = null, limit = 50, userId = 42L)

        assertEquals(1, result.messages.size)
    }

    @Test
    fun `메시지가_limit_이하면_hasMore_false`() {
        every { roomApi.getActiveRoomInfo(1L) } returns publicRoom()
        every { messageRepository.findByRoomIdOrderByIdDesc(1L, any<Pageable>()) } returns
            listOf(message(3L), message(2L), message(1L))

        val result = messageService.list(roomId = 1L, beforeId = null, limit = 50, userId = 1L)

        assertFalse(result.hasMore)
        assertNull(result.nextCursor)
        assertEquals(3, result.messages.size)
    }

    @Test
    fun `메시지가_limit_초과면_hasMore_true이고_nextCursor_반환`() {
        // limit=2 요청 시 내부적으로 3개 조회 → 3개면 hasMore=true
        every { roomApi.getActiveRoomInfo(1L) } returns publicRoom()
        every { messageRepository.findByRoomIdOrderByIdDesc(1L, any<Pageable>()) } returns
            listOf(message(3L), message(2L), message(1L))

        val result = messageService.list(roomId = 1L, beforeId = null, limit = 2, userId = 1L)

        assertTrue(result.hasMore)
        assertEquals(2L, result.nextCursor)
        assertEquals(2, result.messages.size)
    }

    @Test
    fun `before_파라미터_있으면_해당_id_미만으로_조회`() {
        every { roomApi.getActiveRoomInfo(1L) } returns publicRoom()
        every {
            messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(eq(1L), eq(5L), any<Pageable>())
        } returns listOf(message(4L), message(3L))

        val result = messageService.list(roomId = 1L, beforeId = 5L, limit = 50, userId = 1L)

        assertFalse(result.hasMore)
        assertEquals(listOf(4L, 3L), result.messages.map { it.id })
    }

    @Test
    fun `메시지_저장_성공`() {
        val saved = message(id = 1L)
        every { messageRepository.save(any()) } returns saved

        val result = messageService.save(roomId = 1L, userId = 10L, content = "안녕하세요")

        assertEquals(1L, result.id)
        assertEquals("메시지 1", result.content)
    }
}
