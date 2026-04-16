package io.github.chan808.authtemplate.member.infrastructure.persistence

import io.github.chan808.authtemplate.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByIdAndWithdrawnAtIsNull(id: Long): Member?
    fun findByEmailAndWithdrawnAtIsNull(email: String): Member?
    fun existsByEmail(email: String): Boolean
    fun findByProviderAndProviderIdAndWithdrawnAtIsNull(provider: String, providerId: String): Member?
    fun findAllByEmailVerifiedFalseAndProviderIsNullAndWithdrawnAtIsNullAndCreatedAtBefore(cutoff: LocalDateTime): List<Member>
}
