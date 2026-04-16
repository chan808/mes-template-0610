package io.github.chan808.authtemplate.auth.application

import io.github.chan808.authtemplate.auth.api.AuthApi
import io.github.chan808.authtemplate.auth.application.port.AccessTokenPort
import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
import io.github.chan808.authtemplate.auth.presentation.LoginRequest
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.maskEmail
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.api.MemberApi
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class AuthCommandService(
    private val memberApi: MemberApi,
    private val passwordEncoder: PasswordEncoder,
    private val accessTokenPort: AccessTokenPort,
    private val tokenStore: TokenStore,
    private val loginRateLimitService: LoginRateLimitService,
    private val domainMetrics: DomainMetrics,
) : AuthApi {

    private val log = LoggerFactory.getLogger(AuthCommandService::class.java)
    private val secureRandom = SecureRandom()

    fun login(request: LoginRequest, ip: String): Pair<String, String> {
        val email = request.email.lowercase().trim()
        loginRateLimitService.check(ip, email)

        val member = memberApi.findAuthMemberByEmail(email) ?: run {
            domainMetrics.recordLoginFailure("email_not_found")
            log.warn("[AUTH] login failed email={} reason=EMAIL_NOT_FOUND", maskEmail(email))
            throw AuthException(ErrorCode.INVALID_CREDENTIALS)
        }

        if (member.isOAuthAccount) {
            domainMetrics.recordLoginFailure("oauth_account")
            log.warn("[AUTH] login failed email={} reason=OAUTH_ACCOUNT provider={}", maskEmail(email), member.provider)
            throw AuthException(ErrorCode.OAUTH_ACCOUNT_NO_PASSWORD)
        }

        if (!passwordEncoder.matches(request.password, member.encodedPassword)) {
            domainMetrics.recordLoginFailure("invalid_password")
            log.warn("[AUTH] login failed email={} reason=INVALID_PASSWORD", maskEmail(email))
            throw AuthException(ErrorCode.INVALID_CREDENTIALS)
        }

        if (!member.emailVerified) {
            domainMetrics.recordLoginFailure("email_not_verified")
            log.warn("[AUTH] login failed email={} reason=EMAIL_NOT_VERIFIED", maskEmail(email))
            throw AuthException(ErrorCode.EMAIL_NOT_VERIFIED)
        }

        val (sid, rt) = generateRefreshToken()
        tokenStore.save(
            sid = sid,
            session = RefreshTokenSession(
                memberId = member.id,
                role = member.role,
                tokenHash = hashToken(rt),
                absoluteExpiryEpoch = Instant.now().plusSeconds(30L * 24 * 3600).epochSecond,
            ),
            ttlSeconds = 7L * 24 * 3600,
        )
        tokenStore.addSession(member.id, sid)
        domainMetrics.recordLoginSuccess()

        return accessTokenPort.generateAccessToken(member.id, member.role, member.tokenVersion) to rt
    }

    fun reissue(rtToken: String): Pair<String, String> {
        val sid = parseSid(rtToken)

        if (!tokenStore.tryLock(sid)) {
            domainMetrics.recordRefreshTokenReissueFailure("lock_conflict")
            throw AuthException(ErrorCode.REISSUE_CONFLICT)
        }

        try {
            val session = tokenStore.find(sid) ?: run {
                domainMetrics.recordRefreshTokenReissueFailure("not_found")
                throw AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
            }

            if (!MessageDigest.isEqual(
                    session.tokenHash.toByteArray(Charsets.UTF_8),
                    hashToken(rtToken).toByteArray(Charsets.UTF_8),
                )
            ) {
                tokenStore.deleteAllSessionsForMember(session.memberId)
                domainMetrics.recordRefreshTokenReissueFailure("token_mismatch")
                log.error("[SECURITY] refresh token reuse detected, all sessions revoked memberId={}", session.memberId)
                throw AuthException(ErrorCode.REFRESH_TOKEN_MISMATCH)
            }

            if (session.absoluteExpiryEpoch < Instant.now().epochSecond) {
                tokenStore.deleteSession(session.memberId, sid)
                domainMetrics.recordRefreshTokenReissueFailure("expired")
                throw AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
            }

            val newRt = "$sid.${generateRandomPart()}"
            tokenStore.save(sid, session.copy(tokenHash = hashToken(newRt)), 7L * 24 * 3600)
            domainMetrics.recordRefreshTokenReissueSuccess()

            val member = memberApi.findAuthMemberById(session.memberId) ?: run {
                tokenStore.deleteSession(session.memberId, sid)
                domainMetrics.recordRefreshTokenReissueFailure("member_not_found")
                throw AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
            }

            return accessTokenPort.generateAccessToken(member.id, member.role, member.tokenVersion) to newRt
        } finally {
            tokenStore.releaseLock(sid)
        }
    }

    fun logout(rtToken: String?) {
        rtToken ?: return
        val sid = parseSid(rtToken)
        val session = tokenStore.find(sid) ?: return
        tokenStore.deleteSession(session.memberId, sid)
        domainMetrics.recordLogout()
    }

    fun issueTokensForOAuth(memberId: Long): Pair<String, String> {
        val member = memberApi.findAuthMemberById(memberId)
            ?: throw AuthException(ErrorCode.MEMBER_NOT_FOUND)
        val (sid, rt) = generateRefreshToken()
        tokenStore.save(
            sid = sid,
            session = RefreshTokenSession(
                memberId = memberId,
                role = member.role,
                tokenHash = hashToken(rt),
                absoluteExpiryEpoch = Instant.now().plusSeconds(30L * 24 * 3600).epochSecond,
            ),
            ttlSeconds = 7L * 24 * 3600,
        )
        tokenStore.addSession(memberId, sid)
        return accessTokenPort.generateAccessToken(member.id, member.role, member.tokenVersion) to rt
    }

    override fun invalidateAllSessions(memberId: Long) {
        tokenStore.deleteAllSessionsForMember(memberId)
    }

    private fun generateRefreshToken(): Pair<String, String> {
        val sid = UUID.randomUUID().toString()
        return sid to "$sid.${generateRandomPart()}"
    }

    private fun generateRandomPart(): String {
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun parseSid(token: String): String {
        val dotIndex = token.indexOf('.')
        if (dotIndex <= 0) {
            throw AuthException(ErrorCode.TOKEN_INVALID)
        }
        return token.substring(0, dotIndex)
    }

    private fun hashToken(token: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(bytes)
    }
}
