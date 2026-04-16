package io.github.chan808.authtemplate.common

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals

class ClientIpResolverTest {

    @Test
    fun `uses remote address by default`() {
        val resolver = ClientIpResolver(ClientIpProperties())
        val request = MockHttpServletRequest().apply {
            remoteAddr = "203.0.113.10"
            addHeader("X-Forwarded-For", "198.51.100.1")
        }

        assertEquals("203.0.113.10", resolver.resolve(request))
    }

    @Test
    fun `uses forwarded header when remote proxy is trusted`() {
        val resolver = ClientIpResolver(
            ClientIpProperties(
                trustForwardedHeaders = true,
                trustedProxies = listOf("10.0.0.0/8"),
            ),
        )
        val request = MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.12"
            addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.11")
        }

        assertEquals("198.51.100.7", resolver.resolve(request))
    }

    @Test
    fun `ignores forwarded header from untrusted proxy`() {
        val resolver = ClientIpResolver(
            ClientIpProperties(
                trustForwardedHeaders = true,
                trustedProxies = listOf("10.0.0.0/8"),
            ),
        )
        val request = MockHttpServletRequest().apply {
            remoteAddr = "203.0.113.10"
            addHeader("X-Forwarded-For", "198.51.100.7")
        }

        assertEquals("203.0.113.10", resolver.resolve(request))
    }
}
