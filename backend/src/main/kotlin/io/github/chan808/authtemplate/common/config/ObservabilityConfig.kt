package io.github.chan808.authtemplate.common.config

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ObservabilityConfig {

    @Bean
    fun meterRegistryCustomizer(
        @Value("\${spring.application.name:backend}") applicationName: String,
        @Value("\${spring.profiles.active:default}") profile: String,
    ): MeterRegistryCustomizer<MeterRegistry> =
        MeterRegistryCustomizer { registry ->
            registry.config().commonTags(
                "application",
                applicationName,
                "profile",
                profile,
            )
        }
}
