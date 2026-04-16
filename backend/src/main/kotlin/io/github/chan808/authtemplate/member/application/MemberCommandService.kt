package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.events.MemberWithdrawnEvent
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.member.presentation.ChangePasswordRequest
import io.github.chan808.authtemplate.member.presentation.MemberResponse
import io.github.chan808.authtemplate.member.presentation.SignupRequest
import io.github.chan808.authtemplate.member.presentation.UpdateProfileRequest
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberCommandService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val signupRateLimitService: SignupRateLimitService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val emailVerificationService: EmailVerificationService,
    private val eventPublisher: ApplicationEventPublisher,
    private val domainMetrics: DomainMetrics,
) {
    private val log = LoggerFactory.getLogger(MemberCommandService::class.java)

    @Transactional
    fun signup(request: SignupRequest, ip: String) {
        signupRateLimitService.check(ip)
        val email = request.email.lowercase().trim()
        memberRepository.findByEmailAndWithdrawnAtIsNull(email)?.let { existing ->
            if (existing.emailVerified || existing.isOAuthAccount) {
                // 사용자 열거 방지: 이미 가입이 완료된(또는 OAuth) 계정에도 신규 가입과 동일한 201 응답을 반환한다.
                // 기존 비밀번호나 프로필은 절대 건드리지 않고, 어떠한 메일도 발송하지 않는다.
                domainMetrics.recordSignupFailure("duplicate_email_silenced")
                log.info("[AUTH] signup attempted on existing account memberId={}", existing.id)
                return
            }

            emailVerificationService.sendVerification(existing.id, existing.email)
            domainMetrics.recordSignupSuccess()
            log.info("[AUTH] unverified signup retried memberId={} passwordUnchanged=true", existing.id)
            return
        }

        breachedPasswordChecker.check(request.password, email)
        val member = memberRepository.save(
            Member(
                email = email,
                password = passwordEncoder.encode(request.password) ?: error("PasswordEncoder returned null"),
            ),
        )
        emailVerificationService.sendVerification(member.id, member.email)
        domainMetrics.recordSignupSuccess()
        log.info("[AUTH] signup accepted memberId={}", member.id)
    }

    @Transactional
    fun updateProfile(memberId: Long, request: UpdateProfileRequest): MemberResponse {
        val member = getById(memberId)
        member.updateProfile(request.nickname)
        domainMetrics.recordProfileUpdate()
        log.info("[MEMBER] profile updated memberId={}", memberId)
        return MemberResponse.from(member)
    }

    @Transactional
    fun changePassword(memberId: Long, request: ChangePasswordRequest) {
        val member = getById(memberId)
        if (!passwordEncoder.matches(request.currentPassword, member.password)) {
            throw MemberException(ErrorCode.INVALID_CURRENT_PASSWORD)
        }
        breachedPasswordChecker.check(request.newPassword, member.email)
        member.changePassword(passwordEncoder.encode(request.newPassword) ?: error("PasswordEncoder returned null"))
        member.incrementTokenVersion()
        eventPublisher.publishEvent(PasswordChangedEvent(memberId))
        domainMetrics.recordPasswordChange()
        log.info("[AUTH] password changed memberId={}", memberId)
    }

    @Transactional
    fun withdraw(memberId: Long) {
        val member = getById(memberId)
        val withdrawnAt = LocalDateTime.now()
        val uniqueSuffix = UUID.randomUUID().toString()
        val anonymizedEmail = "withdrawn+${member.id}.$uniqueSuffix@example.invalid"
        val anonymizedProviderId = member.providerId?.let { "withdrawn:${member.id}:$uniqueSuffix" }
        member.incrementTokenVersion()
        member.withdraw(anonymizedEmail, anonymizedProviderId, withdrawnAt)
        eventPublisher.publishEvent(MemberWithdrawnEvent(memberId))
        domainMetrics.recordWithdrawal()
        log.info("[MEMBER] member withdrawn memberId={}", memberId)
    }

    fun getById(memberId: Long): Member =
        memberRepository.findByIdAndWithdrawnAtIsNull(memberId) ?: throw MemberException(ErrorCode.MEMBER_NOT_FOUND)

    fun getMyInfo(memberId: Long): MemberResponse = MemberResponse.from(getById(memberId))
}
