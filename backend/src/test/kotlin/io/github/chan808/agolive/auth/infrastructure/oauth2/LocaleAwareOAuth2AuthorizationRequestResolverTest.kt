package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import kotlin.test.assertEquals

class LocaleAwareOAuth2AuthorizationRequestResolverTest {

    private val delegate: OAuth2AuthorizationRequestResolver = mockk()
    private val resolver = LocaleAwareOAuth2AuthorizationRequestResolver(delegate, "ko")
    private val authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://accounts.example.com/oauth2/auth")
        .clientId("client-id")
        .redirectUri("http://localhost:8080/login/oauth2/code/google")
        .state("state-value")
        .build()

    @Test
    fun `stores request locale in session`() {
        val request = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/oauth2/authorization/google"
            setParameter("locale", "en")
        }
        every { delegate.resolve(any<HttpServletRequest>(), any<String>()) } returns authorizationRequest

        resolver.resolve(request, "google")

        assertEquals("en", request.getSession(false)!!.getAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.SESSION_KEY))
    }

    @Test
    fun `falls back to default locale when locale parameter is absent`() {
        val request = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/oauth2/authorization/google"
        }
        every { delegate.resolve(any<HttpServletRequest>(), any<String>()) } returns authorizationRequest

        resolver.resolve(request, "google")

        assertEquals("ko", request.getSession(false)!!.getAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.SESSION_KEY))
    }

    @Test
    fun `stores safe returnTo in session`() {
        val request = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/oauth2/authorization/google"
            setParameter("returnTo", "/rooms/alpha?tab=chat")
        }
        every { delegate.resolve(any<HttpServletRequest>(), any<String>()) } returns authorizationRequest

        resolver.resolve(request, "google")

        assertEquals(
            "/rooms/alpha?tab=chat",
            request.getSession(false)!!.getAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.RETURN_TO_SESSION_KEY),
        )
    }

    @Test
    fun `ignores unsafe returnTo`() {
        val request = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/oauth2/authorization/google"
            setParameter("returnTo", "https://evil.example.com")
        }
        every { delegate.resolve(any<HttpServletRequest>(), any<String>()) } returns authorizationRequest

        resolver.resolve(request, "google")

        assertEquals(
            null,
            request.getSession(false)!!.getAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.RETURN_TO_SESSION_KEY),
        )
    }
}
