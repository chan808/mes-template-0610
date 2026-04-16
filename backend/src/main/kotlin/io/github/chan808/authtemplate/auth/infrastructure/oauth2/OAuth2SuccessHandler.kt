package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import io.github.chan808.authtemplate.auth.application.AuthCommandService
import io.github.chan808.authtemplate.auth.infrastructure.redis.OAuthCodeStore
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.util.UUID

@Component
class OAuth2SuccessHandler(
    private val authService: AuthCommandService,
    private val oAuthCodeStore: OAuthCodeStore,
    @Value("\${app.base-url}") private val frontendBaseUrl: String,
    @Value("\${app.default-locale:ko}") private val defaultLocale: String,
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
    @Value("\${jwt.refresh-token-expiry}") private val rtExpiry: Long,
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(OAuth2SuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oAuth2User = authentication.principal as AuthenticatedOAuth2User
        val (accessToken, rawRt) = authService.issueTokensForOAuth(oAuth2User.memberId)

        // 나머지 엔드포인트와 동일하게 ResponseCookie로 SameSite=Strict를 직접 설정한다.
        val rtCookie = ResponseCookie.from("refresh_token", rawRt)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(rtExpiry)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, rtCookie.toString())

        val code = UUID.randomUUID().toString()
        oAuthCodeStore.save(code, accessToken)

        val locale = resolveLocale(request)
        val returnTo = resolveReturnTo(request)
        log.info("[AUTH] OAuth2 login success memberId={} provider={} locale={}", oAuth2User.memberId, oAuth2User.provider, locale)
        response.sendRedirect(
            UriComponentsBuilder.fromUriString("$frontendBaseUrl/$locale/auth/callback")
                .queryParam("code", code)
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
