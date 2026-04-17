package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest

class LocaleAwareOAuth2AuthorizationRequestResolver(
    private val delegate: OAuth2AuthorizationRequestResolver,
    private val defaultLocale: String,
) : OAuth2AuthorizationRequestResolver {

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? =
        delegate.resolve(request)?.also { storeLocale(request) }

    override fun resolve(request: HttpServletRequest, clientRegistrationId: String): OAuth2AuthorizationRequest? =
        delegate.resolve(request, clientRegistrationId)?.also { storeLocale(request) }

    private fun storeLocale(request: HttpServletRequest) {
        val locale = request.getParameter("locale")
            ?.takeIf { SUPPORTED_LOCALE_PATTERN.matches(it) }
            ?: defaultLocale

        val session = request.getSession(true)
        session.setAttribute(SESSION_KEY, locale)
        session.setAttribute(RETURN_TO_SESSION_KEY, normalizeReturnTo(request.getParameter("returnTo")))
    }

    companion object {
        const val SESSION_KEY = "oauth2.locale"
        const val RETURN_TO_SESSION_KEY = "oauth2.returnTo"
        private val SUPPORTED_LOCALE_PATTERN = Regex("^[a-zA-Z]{2,8}(?:-[a-zA-Z0-9]{2,8})?$")

        fun normalizeReturnTo(value: String?): String? =
            value?.takeIf { it.startsWith("/") && !it.startsWith("//") }
    }
}
