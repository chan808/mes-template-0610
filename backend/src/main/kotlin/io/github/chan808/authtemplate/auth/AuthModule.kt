@file:Suppress("unused")

package io.github.chan808.authtemplate.auth

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["member :: api", "member :: events", "common", "common :: metrics", "common :: ratelimit"],
)
class AuthModule
