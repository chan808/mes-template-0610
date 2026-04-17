package io.github.chan808.authtemplate.user.application.port

interface MailSender {
    fun send(to: String, subject: String, body: String)
}
