package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.redis.EmailVerificationStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UnverifiedMemberCleanupService(
    private val memberRepository: MemberRepository,
    private val emailVerificationStore: EmailVerificationStore,
) {

    private val log = LoggerFactory.getLogger(UnverifiedMemberCleanupService::class.java)

    @Transactional
    fun cleanupOlderThan(cutoff: LocalDateTime): Int {
        val targets = memberRepository.findAllByEmailVerifiedFalseAndProviderIsNullAndWithdrawnAtIsNullAndCreatedAtBefore(cutoff)
        if (targets.isEmpty()) return 0

        targets.forEach { emailVerificationStore.deleteByMemberId(it.id) }
        memberRepository.deleteAllInBatch(targets)
        log.info("[MEMBER] cleaned up unverified members count={} cutoff={}", targets.size, cutoff)
        return targets.size
    }
}
