package io.github.chan808.agolive.user.presentation

import io.github.chan808.agolive.user.domain.AvatarTemplate
import io.github.chan808.agolive.user.domain.User
import java.time.OffsetDateTime

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val nickname: String?,
    val avatarId: Long?,
    val avatarImageUrl: String?,
    val provider: String?,
    val role: String,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(user: User, avatar: AvatarTemplate? = null): UserProfileResponse = UserProfileResponse(
            id = user.id,
            email = user.email,
            nickname = user.nickname,
            avatarId = user.avatarId,
            avatarImageUrl = avatar?.imageUrl,
            provider = user.provider,
            role = user.role.name,
            createdAt = user.createdAt,
        )
    }
}
