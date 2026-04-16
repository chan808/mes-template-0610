package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import org.springframework.security.oauth2.core.user.OAuth2User

/** 일반 OAuth2 (Naver, Kakao) 로그인 후 principal 래퍼 */
class CustomOAuth2User(
    private val delegate: OAuth2User,
    override val memberId: Long,
    override val provider: String,
) : OAuth2User by delegate, AuthenticatedOAuth2User
