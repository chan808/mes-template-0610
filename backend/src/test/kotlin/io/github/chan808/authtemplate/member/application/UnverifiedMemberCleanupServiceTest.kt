package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.member.application.UnverifiedMemberCleanupService
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.redis.EmailVerificationStore
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.Runs
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class UnverifiedMemberCleanupServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val emailVerificationStore: EmailVerificationStore = mockk()
    private val service = UnverifiedMemberCleanupService(memberRepository, emailVerificationStore)

    @Test
    fun `cleanup deletes stale unverified local accounts and verification tokens`() {
        val cutoff = LocalDateTime.of(2026, 3, 10, 0, 0)
        val stale = listOf(
            Member(email = "a@example.com", emailVerified = false, id = 1L),
            Member(email = "b@example.com", emailVerified = false, id = 2L),
        )
        every {
            memberRepository.findAllByEmailVerifiedFalseAndProviderIsNullAndWithdrawnAtIsNullAndCreatedAtBefore(cutoff)
        } returns stale
        every { emailVerificationStore.deleteByMemberId(any()) } just Runs
        every { memberRepository.deleteAllInBatch(stale) } just Runs

        val deleted = service.cleanupOlderThan(cutoff)

        assertEquals(2, deleted)
        verify { emailVerificationStore.deleteByMemberId(1L) }
        verify { emailVerificationStore.deleteByMemberId(2L) }
        verify { memberRepository.deleteAllInBatch(stale) }
    }

    @Test
    fun `cleanup returns zero when there is nothing to delete`() {
        val cutoff = LocalDateTime.of(2026, 3, 10, 0, 0)
        every {
            memberRepository.findAllByEmailVerifiedFalseAndProviderIsNullAndWithdrawnAtIsNullAndCreatedAtBefore(cutoff)
        } returns emptyList()

        val deleted = service.cleanupOlderThan(cutoff)

        assertEquals(0, deleted)
        verify(exactly = 0) { emailVerificationStore.deleteByMemberId(any()) }
        verify(exactly = 0) { memberRepository.deleteAllInBatch(any<List<Member>>()) }
    }
}
