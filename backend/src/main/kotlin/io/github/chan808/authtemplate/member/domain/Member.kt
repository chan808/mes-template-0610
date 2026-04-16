package io.github.chan808.authtemplate.member.domain

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
@Table(name = "members")
class Member(
    @Column(nullable = false, unique = true)
    var email: String,

    // OAuth 계정은 비밀번호 없음 → nullable
    @Column(nullable = true)
    var password: String? = null,

    @Column(nullable = false)
    var emailVerified: Boolean = false,

    // OAuth 제공자 (GOOGLE/NAVER/KAKAO). 로컬 계정은 null
    @Column(nullable = true, length = 20)
    val provider: String? = null,

    // OAuth 제공자의 고유 사용자 ID. 로컬 계정은 null
    @Column(name = "provider_id", nullable = true)
    var providerId: String? = null,

    @Column(nullable = true, length = 50)
    var nickname: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: MemberRole = MemberRole.USER,

    @Column(nullable = false)
    var tokenVersion: Long = 0L,

    @Column(nullable = true)
    var withdrawnAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) : BaseEntity() {
    val isOAuthAccount: Boolean get() = provider != null
    val isWithdrawn: Boolean get() = withdrawnAt != null

    fun updateProfile(nickname: String?) {
        this.nickname = nickname?.trim()?.ifBlank { null }
    }

    fun changePassword(encodedPassword: String) {
        this.password = encodedPassword
    }

    fun incrementTokenVersion() {
        tokenVersion += 1
    }

    fun withdraw(
        anonymizedEmail: String,
        anonymizedProviderId: String?,
        withdrawnAt: LocalDateTime,
    ) {
        email = anonymizedEmail
        providerId = anonymizedProviderId
        password = null
        nickname = null
        emailVerified = false
        this.withdrawnAt = withdrawnAt
    }
}
