@file:Suppress("unused")
package io.github.chan808.authtemplate.user

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common", "common :: metrics", "common :: ratelimit"],
)
class UserModule
