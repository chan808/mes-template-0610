package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.api.AuthMemberView
import io.github.chan808.authtemplate.member.api.MemberApi
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.security.BreachedPasswordChecker
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Auth module accesses member state only through this facade so read/write auth flows
 * stay separated from the member HTTP command service.
 */
@Service
@Transactional(readOnly = true)
class MemberAuthFacade(
    private val memberRepository: MemberRepository,
    private val emailVerificationService: EmailVerificationService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val passwordEncoder: PasswordEncoder,
    private val eventPublisher: ApplicationEventPublisher,
    private val domainMetrics: DomainMetrics,
) : MemberApi {

    private val log = LoggerFactory.getLogger(MemberAuthFacade::class.java)

    override fun findAuthMemberByEmail(email: String): AuthMemberView? =
        memberRepository.findByEmailAndWithdrawnAtIsNull(email)?.toAuthView()

    override fun findAuthMemberById(id: Long): AuthMemberView? =
        memberRepository.findByIdAndWithdrawnAtIsNull(id)?.toAuthView()

    @Transactional
    override fun verifyEmail(token: String) {
        emailVerificationService.verify(token)
    }

    override fun resendVerification(email: String, ip: String) {
        emailVerificationService.resend(email, ip)
    }

    @Transactional
    override fun resetPassword(memberId: Long, newRawPassword: String) {
        val member = memberRepository.findByIdAndWithdrawnAtIsNull(memberId)
            ?: throw AuthException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID)
        if (member.isOAuthAccount) {
            domainMetrics.recordPasswordResetConfirmation("blocked_oauth_account")
            throw AuthException(ErrorCode.OAUTH_PASSWORD_RESET_NOT_ALLOWED)
        }

        breachedPasswordChecker.check(newRawPassword, member.email)
        member.changePassword(passwordEncoder.encode(newRawPassword) ?: error("PasswordEncoder returned null"))
        member.incrementTokenVersion()
        eventPublisher.publishEvent(PasswordChangedEvent(memberId))
        domainMetrics.recordPasswordChange()
    }

    @Transactional
    override fun findOrCreateOAuthMember(
        email: String,
        provider: String,
        providerId: String,
        nickname: String?,
    ): AuthMemberView {
        memberRepository.findByProviderAndProviderIdAndWithdrawnAtIsNull(provider, providerId)
            ?.let { return it.toAuthView() }

        memberRepository.findByEmailAndWithdrawnAtIsNull(email)?.let { existing ->
            val existingProvider = existing.provider ?: "LOCAL"
            log.warn(
                "[AUTH] OAuth email conflict email={} existingProvider={} requestedProvider={}",
                email,
                existingProvider,
                provider,
            )
            throw AuthException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        val member = memberRepository.save(
            Member(
                email = email,
                provider = provider,
                providerId = providerId,
                nickname = nickname,
                emailVerified = true,
            ),
        )
        log.info("[AUTH] OAuth2 signup provider={} memberId={}", provider, member.id)
        return member.toAuthView()
    }

    private fun Member.toAuthView() = AuthMemberView(
        id = id,
        email = email,
        encodedPassword = password,
        role = role.name,
        tokenVersion = tokenVersion,
        emailVerified = emailVerified,
        provider = provider,
    )
}
