package io.github.chan808.agolive.auth.application

import io.github.chan808.agolive.auth.application.port.AuthMailSender
import io.github.chan808.agolive.auth.application.port.PasswordResetTokenStore
import io.github.chan808.agolive.common.AuthException
import io.github.chan808.agolive.common.ErrorCode
import io.github.chan808.agolive.common.metrics.DomainMetrics
import io.github.chan808.agolive.user.api.UserApi
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.util.UUID

@Service
class PasswordResetService(
    private val userApi: UserApi,
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

        val user = userApi.findAuthUserByEmail(normalizedEmail) ?: run {
            domainMetrics.recordPasswordResetRequest("ignored_unknown_email")
            return
        }

        if (user.isOAuthAccount) {
            domainMetrics.recordPasswordResetRequest("ignored_oauth_account")
            log.info("[AUTH] OAuth account password reset blocked userId={}", user.id)
            return
        }

        val token = UUID.randomUUID().toString()
        passwordResetStore.save(token, user.id)

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

        mailSender.send(user.email, "Password reset", body)
        domainMetrics.recordPasswordResetRequest("issued")
        log.info("[AUTH] Password reset mail sent userId={}", user.id)
    }

    fun confirmReset(token: String, newPassword: String) {
        val userId = passwordResetStore.consume(token) ?: run {
            domainMetrics.recordPasswordResetConfirmation("invalid_token")
            throw AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)
        }

        userApi.resetPassword(userId, newPassword)
        domainMetrics.recordPasswordResetConfirmation("success")
        log.info("[AUTH] Password reset completed userId={}", userId)
    }
}
