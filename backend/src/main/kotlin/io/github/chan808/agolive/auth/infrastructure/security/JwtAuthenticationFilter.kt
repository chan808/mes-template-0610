package io.github.chan808.agolive.auth.infrastructure.security

import io.github.chan808.agolive.auth.application.port.TokenStore
import io.github.chan808.agolive.common.ErrorCode
import io.github.chan808.agolive.user.api.UserApi
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// Registered directly from SecurityConfig to keep the filter opt-in and stateless.
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val userApi: UserApi,
    private val tokenStore: TokenStore,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        resolveToken(request)?.let { token ->
            try {
                val claims = jwtProvider.validate(token)
                val userId = claims.subject.toLong()
                val tokenVersion = (claims["tokenVersion"] as? Number)?.toLong() ?: throw JwtException("Missing tokenVersion claim")
                val currentTokenVersion = tokenStore.findAccessTokenVersion(userId)
                    ?: userApi.findAuthUserById(userId)?.also {
                        tokenStore.cacheAccessTokenVersion(userId, it.tokenVersion)
                    }?.tokenVersion

                if (currentTokenVersion == null || currentTokenVersion != tokenVersion) {
                    SecurityContextHolder.clearContext()
                    request.setAttribute("jwt-error", ErrorCode.TOKEN_INVALID)
                    chain.doFilter(request, response)
                    return
                }

                val auth = UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    listOf(SimpleGrantedAuthority(claims["role"] as String)),
                )
                SecurityContextHolder.getContext().authentication = auth
            } catch (ex: ExpiredJwtException) {
                SecurityContextHolder.clearContext()
                request.setAttribute("jwt-error", ErrorCode.TOKEN_EXPIRED)
            } catch (ex: JwtException) {
                SecurityContextHolder.clearContext()
                request.setAttribute("jwt-error", ErrorCode.TOKEN_INVALID)
            }
        }
        chain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)
}
