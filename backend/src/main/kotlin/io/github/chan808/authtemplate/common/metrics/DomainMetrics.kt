package io.github.chan808.authtemplate.common.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class DomainMetrics(
    private val meterRegistry: MeterRegistry,
) {

    fun recordLoginSuccess() = increment("auth.login.attempts", "outcome", "success", "reason", "none")

    fun recordLoginFailure(reason: String) = increment("auth.login.attempts", "outcome", "failure", "reason", reason)

    fun recordRefreshTokenReissueSuccess() =
        increment("auth.refresh.reissue", "outcome", "success", "reason", "none")

    fun recordRefreshTokenReissueFailure(reason: String) =
        increment("auth.refresh.reissue", "outcome", "failure", "reason", reason)

    fun recordLogout() = increment("auth.logout.total")

    fun recordPasswordResetRequest(outcome: String) =
        increment("auth.password.reset.requests", "outcome", outcome)

    fun recordPasswordResetConfirmation(outcome: String) =
        increment("auth.password.reset.confirmations", "outcome", outcome)

    fun recordEmailVerificationResend(outcome: String) =
        increment("auth.email.verification.resend", "outcome", outcome)

    fun recordSignupSuccess() = increment("member.signup.total", "outcome", "success")

    fun recordSignupFailure(reason: String) = increment("member.signup.total", "outcome", "failure", "reason", reason)

    fun recordProfileUpdate() = increment("member.profile.updates")

    fun recordPasswordChange() = increment("member.password.changes")

    fun recordWithdrawal() = increment("member.withdrawals")

    fun recordSessionInvalidation(reason: String) =
        increment("auth.session.invalidations", "reason", reason)

    private fun increment(name: String, vararg tags: String) {
        meterRegistry.counter(name, *tags).increment()
    }
}
