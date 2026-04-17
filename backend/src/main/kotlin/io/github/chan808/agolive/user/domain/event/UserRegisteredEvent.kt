package io.github.chan808.agolive.user.domain.event

data class UserRegisteredEvent(val email: String, val verificationToken: String)
