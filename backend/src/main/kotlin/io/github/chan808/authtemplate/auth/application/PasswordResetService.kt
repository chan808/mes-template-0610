package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.application.port.AuthMailSender
import io.github.chan808.authtemplate.auth.application.port.PasswordResetTokenStore
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.api.MemberApi
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.util.UUID

@Service
class PasswordResetService(
    private val memberApi: MemberApi,
    private val passwordResetStore: PasswordResetTokenStore,
    private val mailSender: AuthMailSender,
    private val passwordResetRateLimitService: PasswordResetRateLimitService,
    private val domainMetrics: DomainMetrics,
    @Value("\${app.base-url}") private val baseUrl: String,
    @Value("\${app.default-locale:ko}") private val defaultLocale: String,
) {
    private val log = LoggerFactory.getLogger(PasswordResetService::class.java)

    fun requestReset(email: String, ip: String) {
        val normalizedEmail = email.lowercase().trim()
        passwordResetRateLimitService.check(ip, normalizedEmail)

        val member = memberApi.findAuthMemberByEmail(normalizedEmail) ?: run {
            domainMetrics.recordPasswordResetRequest("ignored_unknown_email")
            return
        }

        if (member.isOAuthAccount) {
            domainMetrics.recordPasswordResetRequest("ignored_oauth_account")
            log.info("[AUTH] OAuth account password reset blocked memberId={}", member.id)
            return
        }

        val token = UUID.randomUUID().toString()
        passwordResetStore.save(token, member.id)

        val resetLink = UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment(defaultLocale, "reset-password")
            .queryParam("token", token)
            .build()
            .toUriString()

        val body = """
            |We received a password reset request for your account.
            |
            |Use the link below to set a new password:
            |$resetLink
            |
            |This link remains valid for 30 minutes.
            |If you did not request this change, you can ignore this email.
        """.trimMargin()

        mailSender.send(member.email, "Password reset", body)
        domainMetrics.recordPasswordResetRequest("issued")
        log.info("[AUTH] Password reset mail sent memberId={}", member.id)
    }

    fun confirmReset(token: String, newPassword: String) {
        val memberId = passwordResetStore.consume(token) ?: run {
            domainMetrics.recordPasswordResetConfirmation("invalid_token")
            throw AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)
        }

        memberApi.resetPassword(memberId, newPassword)
        domainMetrics.recordPasswordResetConfirmation("success")
        log.info("[AUTH] Password reset completed memberId={}", memberId)
    }
}
