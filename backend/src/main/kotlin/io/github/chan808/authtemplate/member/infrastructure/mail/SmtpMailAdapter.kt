package io.github.chan808.authtemplate.member.infrastructure.mail

import io.github.chan808.authtemplate.member.application.port.MailSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class SmtpMailAdapter(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
) : MailSender {
    private val log = LoggerFactory.getLogger(SmtpMailAdapter::class.java)

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
