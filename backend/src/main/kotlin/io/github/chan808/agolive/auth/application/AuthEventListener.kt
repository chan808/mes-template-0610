package io.github.chan808.agolive.auth.application

import io.github.chan808.agolive.auth.application.port.TokenStore
import io.github.chan808.agolive.common.metrics.DomainMetrics
import io.github.chan808.agolive.user.events.UserWithdrawnEvent
import io.github.chan808.agolive.user.events.PasswordChangedEvent
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
        tokenStore.deleteAllSessionsForUser(event.userId)
        tokenStore.deleteAccessTokenVersion(event.userId)
        domainMetrics.recordSessionInvalidation("password_changed")
        log.info("[AUTH] invalidated all sessions after password change userId={}", event.userId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserWithdrawn(event: UserWithdrawnEvent) {
        tokenStore.deleteAllSessionsForUser(event.userId)
        tokenStore.deleteAccessTokenVersion(event.userId)
        domainMetrics.recordSessionInvalidation("user_withdrawn")
        log.info("[AUTH] invalidated all sessions after user withdrawal userId={}", event.userId)
    }
}
