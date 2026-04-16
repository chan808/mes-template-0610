package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import io.github.chan808.authtemplate.member.api.AuthMemberView
import io.github.chan808.authtemplate.member.api.MemberApi
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * OAuth2 / OIDC 두 흐름에서 공통으로 사용하는 회원 조회/생성 로직
 * CustomOAuth2UserService(Naver, Kakao)와 CustomOidcUserService(Google)가 함께 사용
 * member 모듈의 공개 API(MemberApi)만 사용하여 모듈 경계 준수
 */
@Service
class MemberOAuthService(private val memberApi: MemberApi) {

    private val log = LoggerFactory.getLogger(MemberOAuthService::class.java)

    fun findOrCreate(userInfo: OAuth2UserInfo): AuthMemberView {
        return memberApi.findOrCreateOAuthMember(
            email = userInfo.email,
            provider = userInfo.provider,
            providerId = userInfo.providerId,
            nickname = null,
        )
    }
}
