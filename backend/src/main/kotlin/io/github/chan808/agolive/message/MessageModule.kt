@file:Suppress("unused")
package io.github.chan808.authtemplate.message

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common", "room :: api"],
)
class MessageModule
