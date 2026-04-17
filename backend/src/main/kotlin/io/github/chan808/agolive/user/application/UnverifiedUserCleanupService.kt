package io.github.chan808.authtemplate.user.application

import io.github.chan808.authtemplate.user.infrastructure.persistence.UserRepository
import io.github.chan808.authtemplate.user.infrastructure.redis.EmailVerificationStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class UnverifiedUserCleanupService(
    private val userRepository: UserRepository,
    private val emailVerificationStore: EmailVerificationStore,
) {
    private val log = LoggerFactory.getLogger(UnverifiedUserCleanupService::class.java)

    @Transactional
    fun cleanupOlderThan(cutoff: OffsetDateTime): Int {
        val targets = userRepository.findAllByEmailVerifiedFalseAndProviderIsNullAndWithdrawnAtIsNullAndCreatedAtBefore(cutoff)
        if (targets.isEmpty()) return 0
        targets.forEach { emailVerificationStore.deleteByUserId(it.id) }
        userRepository.deleteAllInBatch(targets)
        log.info("[USER] cleaned up unverified users count={} cutoff={}", targets.size, cutoff)
        return targets.size
    }
}
