@file:Suppress("unused")
package io.github.chan808.agolive.user

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common", "common :: metrics", "common :: ratelimit"],
)
class UserModule
