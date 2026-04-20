package io.github.chan808.agolive.message.application

import io.github.chan808.agolive.common.ErrorCode
import io.github.chan808.agolive.common.RoomException
import io.github.chan808.agolive.message.api.MessageType
import io.github.chan808.agolive.message.domain.Message
import io.github.chan808.agolive.message.infrastructure.persistence.MessageRepository
import io.github.chan808.agolive.room.api.RoomApi
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

    @Test
    fun `존재하지_않는_방_메시지_조회시_ROOM_NOT_FOUND`() {
        every { roomApi.existsActiveRoom(999L) } returns false

        val ex = assertThrows<RoomException> {
            messageService.list(roomId = 999L, beforeId = null, limit = 50)
        }
        assertEquals(ErrorCode.ROOM_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `메시지가_limit_이하면_hasMore_false`() {
        every { roomApi.existsActiveRoom(1L) } returns true
        every { messageRepository.findByRoomIdOrderByIdDesc(1L, any<Pageable>()) } returns
            listOf(message(3L), message(2L), message(1L))

        val result = messageService.list(roomId = 1L, beforeId = null, limit = 50)

        assertFalse(result.hasMore)
        assertNull(result.nextCursor)
        assertEquals(3, result.messages.size)
    }

    @Test
    fun `메시지가_limit_초과면_hasMore_true이고_nextCursor_반환`() {
        // limit=2 요청 시 내부적으로 3개 조회 → 3개면 hasMore=true
        every { roomApi.existsActiveRoom(1L) } returns true
        every { messageRepository.findByRoomIdOrderByIdDesc(1L, any<Pageable>()) } returns
            listOf(message(3L), message(2L), message(1L))

        val result = messageService.list(roomId = 1L, beforeId = null, limit = 2)

        assertTrue(result.hasMore)
        assertEquals(2L, result.nextCursor)
        assertEquals(2, result.messages.size)
    }

    @Test
    fun `before_파라미터_있으면_해당_id_미만으로_조회`() {
        every { roomApi.existsActiveRoom(1L) } returns true
        every {
            messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(eq(1L), eq(5L), any<Pageable>())
        } returns listOf(message(4L), message(3L))

        val result = messageService.list(roomId = 1L, beforeId = 5L, limit = 50)

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
