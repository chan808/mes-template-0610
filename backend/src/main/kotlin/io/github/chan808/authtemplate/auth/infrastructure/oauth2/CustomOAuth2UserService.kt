package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

/** 일반 OAuth2 흐름 (Naver, Kakao) 처리 */
@Service
class CustomOAuth2UserService(
    private val memberOAuthService: MemberOAuthService,
) : DefaultOAuth2UserService() {

    private val log = LoggerFactory.getLogger(CustomOAuth2UserService::class.java)

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val userInfo = oAuth2UserInfoOf(registrationId, oAuth2User.attributes)

        val member = memberOAuthService.findOrCreate(userInfo)
        log.info("[AUTH] OAuth2 로그인 provider={} memberId={}", userInfo.provider, member.id)

        return CustomOAuth2User(oAuth2User, member.id, userInfo.provider)
    }
}

/** 같은 이메일로 다른 방식으로 이미 가입된 경우 */
class OAuthEmailConflictException(email: String, existingProvider: String) :
    RuntimeException("이미 $existingProvider 방식으로 가입된 이메일입니다: $email")
