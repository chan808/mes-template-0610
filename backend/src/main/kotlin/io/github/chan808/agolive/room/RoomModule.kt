@file:Suppress("unused")
package io.github.chan808.authtemplate.room

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common"],
)
class RoomModule
