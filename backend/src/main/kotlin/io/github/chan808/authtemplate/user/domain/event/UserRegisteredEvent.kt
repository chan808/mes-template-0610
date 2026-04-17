package io.github.chan808.authtemplate.user.domain.event

data class UserRegisteredEvent(val email: String, val verificationToken: String)
