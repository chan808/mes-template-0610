package io.github.chan808.authtemplate.user.application

import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.user.api.AuthUserView
import io.github.chan808.authtemplate.user.api.UserApi
import io.github.chan808.authtemplate.user.domain.User
import io.github.chan808.authtemplate.user.events.PasswordChangedEvent
import io.github.chan808.authtemplate.user.infrastructure.persistence.UserRepository
import io.github.chan808.authtemplate.user.infrastructure.security.BreachedPasswordChecker
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserAuthFacade(
    private val userRepository: UserRepository,
    private val emailVerificationService: EmailVerificationService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val passwordEncoder: PasswordEncoder,
    private val eventPublisher: ApplicationEventPublisher,
    private val domainMetrics: DomainMetrics,
) : UserApi {

    private val log = LoggerFactory.getLogger(UserAuthFacade::class.java)

    override fun findAuthUserByEmail(email: String): AuthUserView? =
        userRepository.findByEmailAndWithdrawnAtIsNull(email)?.toAuthView()

    override fun findAuthUserById(id: Long): AuthUserView? =
        userRepository.findByIdAndWithdrawnAtIsNull(id)?.toAuthView()

    @Transactional
    override fun verifyEmail(token: String) {
        emailVerificationService.verify(token)
    }

    override fun resendVerification(email: String, ip: String) {
        emailVerificationService.resend(email, ip)
    }

    @Transactional
    override fun resetPassword(userId: Long, newRawPassword: String) {
        val user = userRepository.findByIdAndWithdrawnAtIsNull(userId)
            ?: throw AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)
        if (user.isOAuthAccount) {
            domainMetrics.recordPasswordResetConfirmation("blocked_oauth_account")
            throw AuthException(ErrorCode.OAUTH_PASSWORD_RESET_NOT_ALLOWED)
        }
        breachedPasswordChecker.check(newRawPassword, user.email)
        user.changePassword(passwordEncoder.encode(newRawPassword) ?: error("PasswordEncoder returned null"))
        user.incrementTokenVersion()
        eventPublisher.publishEvent(PasswordChangedEvent(userId))
        domainMetrics.recordPasswordChange()
    }

    @Transactional
    override fun findOrCreateOAuthUser(email: String, provider: String, providerId: String, nickname: String?): AuthUserView {
        userRepository.findByProviderAndProviderIdAndWithdrawnAtIsNull(provider, providerId)
            ?.let { return it.toAuthView() }

        userRepository.findByEmailAndWithdrawnAtIsNull(email)?.let { existing ->
            log.warn("[AUTH] OAuth email conflict email={} existingProvider={} requestedProvider={}", email, existing.provider ?: "LOCAL", provider)
            throw AuthException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        val user = userRepository.save(User(email = email, provider = provider, providerId = providerId, nickname = nickname, emailVerified = true))
        log.info("[AUTH] OAuth2 signup provider={} userId={}", provider, user.id)
        return user.toAuthView()
    }

    private fun User.toAuthView() = AuthUserView(
        id = id,
        email = email,
        encodedPassword = passwordHash,
        role = role.name,
        tokenVersion = tokenVersion,
        emailVerified = emailVerified,
        provider = provider,
        nickname = nickname,
        avatarId = avatarId,
    )
}
