@file:Suppress("unused")
package io.github.chan808.authtemplate.member
import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common", "common :: metrics", "common :: ratelimit"],
)
class MemberModule
