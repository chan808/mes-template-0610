package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import io.github.chan808.authtemplate.auth.application.AuthCommandService
import io.github.chan808.authtemplate.auth.infrastructure.redis.OAuthCodeStore
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import kotlin.test.assertTrue

class OAuth2HandlersTest {

    private val authService: AuthCommandService = mockk()
    private val oAuthCodeStore: OAuthCodeStore = mockk()

    @Test
    fun `success handler preserves locale in callback redirect`() {
        val handler = OAuth2SuccessHandler(
            authService = authService,
            oAuthCodeStore = oAuthCodeStore,
            frontendBaseUrl = "http://localhost:3000",
            defaultLocale = "ko",
            cookieSecure = false,
            rtExpiry = 604800,
        )
        val principal = object : AuthenticatedOAuth2User {
            override val memberId: Long = 1L
            override val provider: String = "google"
        }
        val authentication: Authentication = mockk {
            every { this@mockk.principal } returns principal
        }
        val request = MockHttpServletRequest().apply {
            getSession(true)!!.setAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.SESSION_KEY, "en")
            getSession(false)!!.setAttribute(
                LocaleAwareOAuth2AuthorizationRequestResolver.RETURN_TO_SESSION_KEY,
                "/rooms/alpha",
            )
        }
        val response = MockHttpServletResponse()

        every { authService.issueTokensForOAuth(1L) } returns ("access-token" to "refresh-token")
        every { oAuthCodeStore.save(any(), "access-token") } just runs

        handler.onAuthenticationSuccess(request, response, authentication)

        assertTrue(response.redirectedUrl?.startsWith("http://localhost:3000/en/auth/callback?code=") == true)
        assertTrue(response.redirectedUrl?.contains("returnTo=/rooms/alpha") == true)
        verify { oAuthCodeStore.save(any(), "access-token") }
    }

    @Test
    fun `failure handler preserves locale in login redirect`() {
        val handler = OAuth2FailureHandler(
            frontendBaseUrl = "http://localhost:3000",
            defaultLocale = "ko",
        )
        val request = MockHttpServletRequest().apply {
            getSession(true)!!.setAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.SESSION_KEY, "en")
            getSession(false)!!.setAttribute(
                LocaleAwareOAuth2AuthorizationRequestResolver.RETURN_TO_SESSION_KEY,
                "/rooms/alpha",
            )
        }
        val response = MockHttpServletResponse()
        val exception = object : AuthenticationException("failed") {}

        handler.onAuthenticationFailure(request, response, exception)

        assertTrue(response.redirectedUrl?.startsWith("http://localhost:3000/en/login?error=") == true)
        assertTrue(response.redirectedUrl?.contains("returnTo=/rooms/alpha") == true)
    }
}
