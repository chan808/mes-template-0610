@file:Suppress("unused")
package io.github.chan808.authtemplate.internal

import org.springframework.modulith.ApplicationModule

@ApplicationModule(allowedDependencies = ["common", "user :: api", "message :: api"])
class InternalModule
