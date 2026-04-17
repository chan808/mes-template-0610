@file:Suppress("unused")
package io.github.chan808.agolive.internal

import org.springframework.modulith.ApplicationModule

@ApplicationModule(allowedDependencies = ["common", "user :: api", "message :: api", "room :: api"])
class InternalModule
