package io.github.chan808.agolive.user.infrastructure.persistence

import io.github.chan808.agolive.user.domain.AvatarTemplate
import org.springframework.data.jpa.repository.JpaRepository

interface AvatarTemplateRepository : JpaRepository<AvatarTemplate, Long> {
    fun findAllByIsActiveTrue(): List<AvatarTemplate>
}
