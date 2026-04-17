package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

/**
 * OIDC 흐름 (Google: openid scope) 처리
 * OidcUserService를 상속해 super.loadUser()로 표준 OIDC 사용자 정보를 로드한 뒤,
 * 우리 회원 DB와 연동하고 CustomOidcUser 래퍼로 반환
 */
@Service
class CustomOidcUserService(
    private val userOAuthService: UserOAuthService,
) : OidcUserService() {

    private val log = LoggerFactory.getLogger(CustomOidcUserService::class.java)

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = super.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val userInfo = oAuth2UserInfoOf(registrationId, oidcUser.attributes)

        val user = userOAuthService.findOrCreate(userInfo)
        log.info("[AUTH] OIDC 로그인 provider={} userId={}", userInfo.provider, user.id)

        return CustomOidcUser(oidcUser, user.id, userInfo.provider)
    }
}
