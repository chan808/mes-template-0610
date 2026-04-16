package io.github.chan808.authtemplate.member.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class UnverifiedMemberCleanupScheduler(
    private val cleanupService: UnverifiedMemberCleanupService,
    @Value("\${app.member.cleanup.unverified.enabled:true}") private val enabled: Boolean,
    @Value("\${app.member.cleanup.unverified.age-days:7}") private val ageDays: Long,
) {

    private val log = LoggerFactory.getLogger(UnverifiedMemberCleanupScheduler::class.java)

    @Scheduled(cron = "\${app.member.cleanup.unverified.cron:0 0 3 * * *}")
    fun cleanup() {
        if (!enabled) return

        val cutoff = LocalDateTime.now().minusDays(ageDays)
        val count = cleanupService.cleanupOlderThan(cutoff)
        if (count > 0) {
            log.info("[MEMBER] scheduled cleanup finished count={} cutoff={}", count, cutoff)
        }
    }
}
