package io.github.chan808.authtemplate.auth.application.port

/**
 * auth 모듈의 메일 발송 추상화.
 * 비밀번호 재설정 등 auth 책임의 메일을 발송한다.
 * SMTP → SES 등으로 교체 시 구현체만 변경.
 */
interface AuthMailSender {
    fun send(to: String, subject: String, body: String)
}
