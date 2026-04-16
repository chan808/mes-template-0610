package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.member.application.port.MailSender
import io.github.chan808.authtemplate.member.domain.event.MemberRegisteredEvent
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class MemberEventListenerTest {

    private val mailSender: MailSender = mockk()
    private val listener = MemberEventListener(
        mailSender = mailSender,
        baseUrl = "http://localhost:3000",
        defaultLocale = "ko",
    )

    @Test
    fun `verification mail points to frontend verify-email route`() {
        io.mockk.every { mailSender.send(any(), any(), any()) } just runs

        listener.onMemberRegistered(MemberRegisteredEvent("test@example.com", "token-123"))

        verify {
            mailSender.send(
                "test@example.com",
                "Verify your email",
                match { it.contains("http://localhost:3000/ko/verify-email?token=token-123") },
            )
        }
    }
}
