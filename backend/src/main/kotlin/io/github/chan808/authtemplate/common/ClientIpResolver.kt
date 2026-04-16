package io.github.chan808.authtemplate.common

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.net.InetAddress

@ConfigurationProperties(prefix = "app.http.client-ip")
data class ClientIpProperties(
    val trustForwardedHeaders: Boolean = false,
    val trustedProxies: List<String> = emptyList(),
)

@Component
class ClientIpResolver(
    private val props: ClientIpProperties,
) {

    private val trustedProxyMatchers = props.trustedProxies
        .mapNotNull(TrustedAddressMatcher::from)

    fun resolve(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr ?: return ""
        if (!props.trustForwardedHeaders || !isTrustedProxy(remoteAddr)) {
            return remoteAddr
        }

        val forwardedFor = request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        if (forwardedFor.isEmpty()) {
            return remoteAddr
        }

        for (address in forwardedFor.asReversed()) {
            if (!isTrustedProxy(address)) {
                return address
            }
        }

        return forwardedFor.first()
    }

    private fun isTrustedProxy(address: String): Boolean =
        trustedProxyMatchers.any { it.matches(address) }
}

private class TrustedAddressMatcher private constructor(
    private val networkAddress: ByteArray,
    private val prefixLength: Int,
) {

    fun matches(candidate: String): Boolean {
        val candidateAddress = runCatching { InetAddress.getByName(candidate).address }.getOrNull() ?: return false
        if (candidateAddress.size != networkAddress.size) {
            return false
        }

        val fullBytes = prefixLength / 8
        for (index in 0 until fullBytes) {
            if (candidateAddress[index] != networkAddress[index]) {
                return false
            }
        }

        val remainingBits = prefixLength % 8
        if (remainingBits == 0) {
            return true
        }

        val mask = (0xFF shl (8 - remainingBits)) and 0xFF
        return (candidateAddress[fullBytes].toInt() and mask) == (networkAddress[fullBytes].toInt() and mask)
    }

    companion object {
        fun from(value: String): TrustedAddressMatcher? {
            val normalized = value.trim()
            if (normalized.isEmpty()) {
                return null
            }

            val parts = normalized.split('/', limit = 2)
            val address = runCatching { InetAddress.getByName(parts[0]).address }.getOrNull() ?: return null
            val maxPrefix = address.size * 8
            val prefixLength = parts.getOrNull(1)?.toIntOrNull() ?: maxPrefix
            if (prefixLength !in 0..maxPrefix) {
                return null
            }

            return TrustedAddressMatcher(address, prefixLength)
        }
    }
}
