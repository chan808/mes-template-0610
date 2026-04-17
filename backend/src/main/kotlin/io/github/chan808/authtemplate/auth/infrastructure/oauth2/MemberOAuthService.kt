package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import io.github.chan808.authtemplate.user.api.AuthUserView
import io.github.chan808.authtemplate.user.api.UserApi
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * OAuth2 / OIDC 두 흐름에서 공통으로 사용하는 사용자 조회/생성 로직
 * CustomOAuth2UserService(Naver, Kakao)와 CustomOidcUserService(Google)가 함께 사용
 * user 모듈의 공개 API(UserApi)만 사용하여 모듈 경계 준수
 */
@Service
class UserOAuthService(private val userApi: UserApi) {

    private val log = LoggerFactory.getLogger(UserOAuthService::class.java)

    fun findOrCreate(userInfo: OAuth2UserInfo): AuthUserView {
        return userApi.findOrCreateOAuthUser(
            email = userInfo.email,
            provider = userInfo.provider,
            providerId = userInfo.providerId,
            nickname = null,
        )
    }
}
