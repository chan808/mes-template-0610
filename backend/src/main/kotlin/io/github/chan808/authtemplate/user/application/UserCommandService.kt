package io.github.chan808.authtemplate.user.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.UserException
import io.github.chan808.authtemplate.common.maskEmail
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.user.domain.User
import io.github.chan808.authtemplate.user.events.UserWithdrawnEvent
import io.github.chan808.authtemplate.user.infrastructure.persistence.AvatarTemplateRepository
import io.github.chan808.authtemplate.user.infrastructure.persistence.UserRepository
import io.github.chan808.authtemplate.user.infrastructure.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.user.presentation.ChangePasswordRequest
import io.github.chan808.authtemplate.user.presentation.SignupRequest
import io.github.chan808.authtemplate.user.presentation.UpdateProfileRequest
import io.github.chan808.authtemplate.user.presentation.UserProfileResponse
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserCommandService(
    private val userRepository: UserRepository,
    private val avatarTemplateRepository: AvatarTemplateRepository,
    private val passwordEncoder: PasswordEncoder,
    private val signupRateLimitService: SignupRateLimitService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val emailVerificationService: EmailVerificationService,
    private val eventPublisher: ApplicationEventPublisher,
    private val domainMetrics: DomainMetrics,
) {
    private val log = LoggerFactory.getLogger(UserCommandService::class.java)

    @Transactional
    fun signup(request: SignupRequest, ip: String) {
        signupRateLimitService.check(ip)
        val email = request.email.lowercase().trim()
        userRepository.findByEmailAndWithdrawnAtIsNull(email)?.let { existing ->
            if (existing.emailVerified || existing.isOAuthAccount) {
                // 사용자 열거 방지: 이미 가입 완료된 계정에도 동일한 201 응답 반환
                domainMetrics.recordSignupFailure("duplicate_email_silenced")
                log.info("[AUTH] signup attempted on existing account userId={}", existing.id)
                return
            }
            emailVerificationService.sendVerification(existing.id, existing.email)
            domainMetrics.recordSignupSuccess()
            log.info("[AUTH] unverified signup retried userId={}", existing.id)
            return
        }
        breachedPasswordChecker.check(request.password, email)
        val user = userRepository.save(User(email = email, passwordHash = passwordEncoder.encode(request.password) ?: error("PasswordEncoder returned null")))
        emailVerificationService.sendVerification(user.id, user.email)
        domainMetrics.recordSignupSuccess()
        log.info("[AUTH] signup accepted userId={}", user.id)
    }

    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): UserProfileResponse {
        val user = getById(userId)
        val avatar = request.avatarId?.let {
            avatarTemplateRepository.findById(it).orElseThrow { UserException(ErrorCode.AVATAR_NOT_FOUND) }
        }
        user.updateProfile(request.nickname, avatar?.id)
        domainMetrics.recordProfileUpdate()
        log.info("[USER] profile updated userId={}", userId)
        return UserProfileResponse.from(user, avatar)
    }

    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest) {
        val user = getById(userId)
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw UserException(ErrorCode.INVALID_CURRENT_PASSWORD)
        }
        breachedPasswordChecker.check(request.newPassword, user.email)
        user.changePassword(passwordEncoder.encode(request.newPassword) ?: error("PasswordEncoder returned null"))
        user.incrementTokenVersion()
        eventPublisher.publishEvent(io.github.chan808.authtemplate.user.events.PasswordChangedEvent(userId))
        domainMetrics.recordPasswordChange()
        log.info("[AUTH] password changed userId={}", userId)
    }

    @Transactional
    fun withdraw(userId: Long) {
        val user = getById(userId)
        val uniqueSuffix = UUID.randomUUID().toString()
        user.incrementTokenVersion()
        user.withdraw(
            anonymizedEmail = "withdrawn+${user.id}.$uniqueSuffix@example.invalid",
            anonymizedProviderId = user.providerId?.let { "withdrawn:${user.id}:$uniqueSuffix" },
            withdrawnAt = LocalDateTime.now(),
        )
        eventPublisher.publishEvent(UserWithdrawnEvent(userId))
        domainMetrics.recordWithdrawal()
        log.info("[USER] user withdrawn userId={}", userId)
    }

    fun getMyProfile(userId: Long): UserProfileResponse {
        val user = getById(userId)
        val avatar = user.avatarId?.let { avatarTemplateRepository.findById(it).orElse(null) }
        return UserProfileResponse.from(user, avatar)
    }

    fun getById(userId: Long): User =
        userRepository.findByIdAndWithdrawnAtIsNull(userId) ?: throw UserException(ErrorCode.USER_NOT_FOUND)
}
