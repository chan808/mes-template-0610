package io.github.chan808.authtemplate.user.infrastructure.persistence

import io.github.chan808.authtemplate.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime

interface UserRepository : JpaRepository<User, Long> {
    fun findByIdAndWithdrawnAtIsNull(id: Long): User?
    fun findByEmailAndWithdrawnAtIsNull(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByProviderAndProviderIdAndWithdrawnAtIsNull(provider: String, providerId: String): User?
    fun findAllByEmailVerifiedFalseAndProviderIsNullAndWithdrawnAtIsNullAndCreatedAtBefore(cutoff: OffsetDateTime): List<User>
}
