package io.github.chan808.authtemplate.auth.infrastructure.security

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI

@Component
class AuthSecurityStartupValidator(
    private val environment: Environment,
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
    @Value("\${app.base-url}") private val appBaseUrl: String,
    @Value("\${cors.allowed-origin}") private val corsAllowedOrigin: String,
) : InitializingBean {

    override fun afterPropertiesSet() {
        if (!environment.matchesProfiles("prod")) {
            return
        }

        check(cookieSecure) {
            "cookie.secure must be true when the prod profile is active."
        }
        validateHttpsUrl("app.base-url", appBaseUrl)
        validateHttpsUrl("cors.allowed-origin", corsAllowedOrigin)
    }

    private fun validateHttpsUrl(propertyName: String, value: String) {
        val uri = runCatching { URI(value) }
            .getOrElse {
                throw IllegalStateException("$propertyName must be a valid absolute HTTPS URL when the prod profile is active.")
            }
        val host = uri.host

        check(uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true)) {
            "$propertyName must use HTTPS when the prod profile is active."
        }
        check(!host.isNullOrBlank()) {
            "$propertyName must include a host when the prod profile is active."
        }
        check(!isLoopbackHost(host)) {
            "$propertyName must not point to a loopback host when the prod profile is active."
        }
    }

    private fun isLoopbackHost(host: String): Boolean =
        host.lowercase() in setOf("localhost", "127.0.0.1", "::1")
}
