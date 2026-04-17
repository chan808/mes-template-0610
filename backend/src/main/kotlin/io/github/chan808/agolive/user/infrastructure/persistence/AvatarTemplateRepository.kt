package io.github.chan808.authtemplate.user.infrastructure.persistence

import io.github.chan808.authtemplate.user.domain.AvatarTemplate
import org.springframework.data.jpa.repository.JpaRepository

interface AvatarTemplateRepository : JpaRepository<AvatarTemplate, Long> {
    fun findAllByIsActiveTrue(): List<AvatarTemplate>
}
