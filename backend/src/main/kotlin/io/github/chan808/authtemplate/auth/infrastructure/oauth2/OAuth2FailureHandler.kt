package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2FailureHandler(
    @Value("\${app.base-url}") private val frontendBaseUrl: String,
    @Value("\${app.default-locale:ko}") private val defaultLocale: String,
) : AuthenticationFailureHandler {

    private val log = LoggerFactory.getLogger(OAuth2FailureHandler::class.java)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val cause = exception.cause
        val message = when (cause) {
            is OAuthEmailConflictException -> cause.message ?: "이미 가입된 이메일입니다."
            else -> "소셜 로그인에 실패했습니다."
        }

        val locale = resolveLocale(request)
        val returnTo = resolveReturnTo(request)
        log.warn("[AUTH] OAuth2 login failure locale={} message={}", locale, message)
        response.sendRedirect(
            UriComponentsBuilder.fromUriString("$frontendBaseUrl/$locale/login")
                .queryParam("error", message)
                .apply {
                    if (returnTo != null) {
                        queryParam("returnTo", returnTo)
                    }
                }
                .build()
                .toUriString(),
        )
    }

    private fun resolveLocale(request: HttpServletRequest): String {
        val session = request.getSession(false) ?: return defaultLocale
        val locale = session.getAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.SESSION_KEY) as? String
        session.removeAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.SESSION_KEY)
        return locale ?: defaultLocale
    }

    private fun resolveReturnTo(request: HttpServletRequest): String? {
        val session = request.getSession(false) ?: return null
        val returnTo = session.getAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.RETURN_TO_SESSION_KEY) as? String
        session.removeAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.RETURN_TO_SESSION_KEY)
        return LocaleAwareOAuth2AuthorizationRequestResolver.normalizeReturnTo(returnTo)
    }
}
