package io.github.chan808.agolive.user.application.port

interface MailSender {
    fun send(to: String, subject: String, body: String)
}
