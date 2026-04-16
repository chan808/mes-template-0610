package io.github.chan808.authtemplate.member.application.port

/**
 * 메일 발송 추상화.
 * 구현체(SmtpMailAdapter)는 member/infrastructure/mail에서 Spring 빈으로 등록.
 * SMTP → SES, SendGrid 등으로 교체 시 구현체만 변경.
 */
interface MailSender {
    fun send(to: String, subject: String, body: String)
}
