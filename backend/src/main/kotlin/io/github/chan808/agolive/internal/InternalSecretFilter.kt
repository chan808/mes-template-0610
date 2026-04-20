package io.github.chan808.agolive.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class InternalSecretFilter(
    @Value("\${internal.secret}") private val secret: String,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/internal/rooms")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (request.getHeader("X-Internal-Secret") != secret) {
            response.sendError(HttpStatus.UNAUTHORIZED.value())
            return
        }
        chain.doFilter(request, response)
    }
}
