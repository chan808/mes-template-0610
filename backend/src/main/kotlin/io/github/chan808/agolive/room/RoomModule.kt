@file:Suppress("unused")
package io.github.chan808.agolive.room

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common"],
)
class RoomModule
