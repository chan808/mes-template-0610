package io.github.chan808.authtemplate.room.application

import io.github.chan808.authtemplate.room.infrastructure.persistence.RoomRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 소프트 삭제 후 30일 경과한 방을 물리 삭제
@Component
class RoomCleanupScheduler(private val roomRepository: RoomRepository) {

    private val log = LoggerFactory.getLogger(RoomCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun purgeDeletedRooms() {
        val cutoff = LocalDateTime.now().minusDays(30)
        val rooms = roomRepository.findAllByDeletedAtBefore(cutoff)
        if (rooms.isEmpty()) return
        roomRepository.deleteAll(rooms)
        log.info("[ROOM] purged {} soft-deleted rooms", rooms.size)
    }
}
