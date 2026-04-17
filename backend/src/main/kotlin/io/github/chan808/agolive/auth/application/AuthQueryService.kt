package io.github.chan808.agolive.auth.application

import io.github.chan808.agolive.auth.application.port.AccessTokenPort
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
