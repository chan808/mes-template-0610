package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.events.MemberWithdrawnEvent
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AuthEventListener(
    private val tokenStore: TokenStore,
    private val domainMetrics: DomainMetrics,
) {

    private val log = LoggerFactory.getLogger(AuthEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPasswordChanged(event: PasswordChangedEvent) {
        tokenStore.deleteAllSessionsForMember(event.memberId)
        tokenStore.deleteAccessTokenVersion(event.memberId)
        domainMetrics.recordSessionInvalidation("password_changed")
        log.info("[AUTH] invalidated all sessions after password change memberId={}", event.memberId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMemberWithdrawn(event: MemberWithdrawnEvent) {
        tokenStore.deleteAllSessionsForMember(event.memberId)
        tokenStore.deleteAccessTokenVersion(event.memberId)
        domainMetrics.recordSessionInvalidation("member_withdrawn")
        log.info("[AUTH] invalidated all sessions after member withdrawal memberId={}", event.memberId)
    }
}
