package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.application.port.AccessTokenPort
import io.jsonwebtoken.JwtException
import org.springframework.stereotype.Service

@Service
class AuthQueryService(
    private val accessTokenPort: AccessTokenPort,
) {

    fun validateToken(token: String): Long? =
        try {
            accessTokenPort.getUserId(token)
        } catch (_: JwtException) {
            null
        }
}
