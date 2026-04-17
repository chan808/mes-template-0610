package io.github.chan808.authtemplate.user.domain

import io.github.chan808.authtemplate.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash", nullable = true)
    var passwordHash: String? = null,

    @Column(nullable = true, length = 50)
    var nickname: String? = null,

    @Column(name = "avatar_id", nullable = true)
    var avatarId: Long? = null,

    @Column(nullable = true, length = 20)
    val provider: String? = null,

    @Column(name = "provider_id", nullable = true)
    var providerId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: UserRole = UserRole.USER,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "token_version", nullable = false)
    var tokenVersion: Long = 0L,

    @Column(name = "withdrawn_at", nullable = true)
    var withdrawnAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) : BaseEntity() {

    val isOAuthAccount: Boolean get() = provider != null
    val isWithdrawn: Boolean get() = withdrawnAt != null

    fun updateProfile(nickname: String?, avatarId: Long?) {
        this.nickname = nickname?.trim()?.ifBlank { null }
        this.avatarId = avatarId
    }

    fun changePassword(encodedPassword: String) {
        this.passwordHash = encodedPassword
    }

    fun incrementTokenVersion() {
        tokenVersion += 1
    }

    fun withdraw(anonymizedEmail: String, anonymizedProviderId: String?, withdrawnAt: LocalDateTime) {
        email = anonymizedEmail
        providerId = anonymizedProviderId
        passwordHash = null
        nickname = null
        emailVerified = false
        this.withdrawnAt = withdrawnAt
    }
}
