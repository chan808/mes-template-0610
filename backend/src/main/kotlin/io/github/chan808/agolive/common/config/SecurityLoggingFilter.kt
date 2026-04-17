package io.github.chan808.authtemplate.common.config

import io.github.chan808.authtemplate.common.ClientIpResolver
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityLoggingFilter(
    private val clientIpResolver: ClientIpResolver,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            MDC.put("requestId", UUID.randomUUID().toString().take(8))
            MDC.put("clientIp", clientIpResolver.resolve(request))
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
