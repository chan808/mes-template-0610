package io.github.chan808.authtemplate.auth.infrastructure.oauth2

/** OAuth2/OIDC 양쪽에서 SuccessHandler가 memberId와 provider를 꺼낼 수 있도록 하는 공통 인터페이스 */
interface AuthenticatedOAuth2User {
    val memberId: Long
    val provider: String
}
