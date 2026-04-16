package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.member.events.MemberWithdrawnEvent
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import org.junit.jupiter.api.Test
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthEventListenerTest {

    @Test
    fun `password changed listener runs after commit`() {
        val annotation = AuthEventListener::class.java
            .getDeclaredMethod("onPasswordChanged", PasswordChangedEvent::class.java)
            .getAnnotation(TransactionalEventListener::class.java)

        assertNotNull(annotation)
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase)
    }

    @Test
    fun `member withdrawn listener runs after commit`() {
        val annotation = AuthEventListener::class.java
            .getDeclaredMethod("onMemberWithdrawn", MemberWithdrawnEvent::class.java)
            .getAnnotation(TransactionalEventListener::class.java)

        assertNotNull(annotation)
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase)
    }
}
