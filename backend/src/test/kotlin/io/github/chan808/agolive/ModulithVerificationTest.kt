package io.github.chan808.agolive

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModulithVerificationTest {

    @Test
    fun `module boundaries are respected`() {
        ApplicationModules.of(AgoliveApplication::class.java).verify()
    }
}
