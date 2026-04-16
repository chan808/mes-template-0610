package io.github.chan808.authtemplate.auth.infrastructure.security

import io.github.chan808.authtemplate.auth.application.port.AccessTokenPort
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(private val props: JwtProperties) : AccessTokenPort {

    // HS256: 단일 비밀키로 MSA 확장 시 키 공유 문제 → 분산 환경 전환 시 RS256 고려
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.secret.toByteArray(Charsets.UTF_8))
    }

    override fun generateAccessToken(memberId: Long, role: String, tokenVersion: Long): String {
        val now = Date()
        return Jwts.builder()
            .subject(memberId.toString())
            .claim("role", role)
            .claim("tokenVersion", tokenVersion)
            .issuedAt(now)
            .expiration(Date(now.time + props.accessTokenExpiry * 1000))
            .signWith(signingKey)
            .compact()
    }

    fun validate(token: String): Claims =
        Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload

    override fun getMemberId(token: String): Long = validate(token).subject.toLong()
}
