package io.github.chan808.authtemplate

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModulithVerificationTest {

    @Test
    fun `module boundaries are respected`() {
        ApplicationModules.of(AuthTemplateApplication::class.java).verify()
    }
}
