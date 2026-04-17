package io.github.chan808.authtemplate.user.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class UnverifiedUserCleanupScheduler(
    private val cleanupService: UnverifiedUserCleanupService,
    @Value("\${app.user.cleanup.unverified.enabled:true}") private val enabled: Boolean,
    @Value("\${app.user.cleanup.unverified.age-days:7}") private val ageDays: Long,
) {
    private val log = LoggerFactory.getLogger(UnverifiedUserCleanupScheduler::class.java)

    @Scheduled(cron = "\${app.user.cleanup.unverified.cron:0 0 3 * * *}")
    fun cleanup() {
        if (!enabled) return
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(ageDays)
        val count = cleanupService.cleanupOlderThan(cutoff)
        if (count > 0) {
            log.info("[USER] scheduled cleanup finished count={} cutoff={}", count, cutoff)
        }
    }
}
