package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.member.application.port.MailSender
import io.github.chan808.authtemplate.member.domain.event.MemberRegisteredEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.util.UriComponentsBuilder

@Component
class MemberEventListener(
    private val mailSender: MailSender,
    @Value("\${app.base-url}") private val baseUrl: String,
    @Value("\${app.default-locale:ko}") private val defaultLocale: String,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMemberRegistered(event: MemberRegisteredEvent) {
        val verificationLink = UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment(defaultLocale, "verify-email")
            .queryParam("token", event.verificationToken)
            .build()
            .toUriString()

        val body = """
            |Complete your email verification using the link below:
            |
            |$verificationLink
            |
            |This link remains valid for 24 hours.
        """.trimMargin()

        mailSender.send(event.email, "Verify your email", body)
    }
}
