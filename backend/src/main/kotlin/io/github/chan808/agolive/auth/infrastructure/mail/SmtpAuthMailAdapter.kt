package io.github.chan808.authtemplate.auth.infrastructure.mail

import io.github.chan808.authtemplate.auth.application.port.AuthMailSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class SmtpAuthMailAdapter(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
) : AuthMailSender {
    private val log = LoggerFactory.getLogger(SmtpAuthMailAdapter::class.java)

    @Async("mailTaskExecutor")
    override fun send(to: String, subject: String, body: String) {
        runCatching {
            mailSender.send(mailSender.createMimeMessage().also { msg ->
                MimeMessageHelper(msg, false, "UTF-8").apply {
                    setFrom(from)
                    setTo(to)
                    setSubject(subject)
                    setText(body)
                }
            })
        }.onFailure { log.error("메일 발송 실패 [to={}]: {}", to, it.message) }
    }
}
