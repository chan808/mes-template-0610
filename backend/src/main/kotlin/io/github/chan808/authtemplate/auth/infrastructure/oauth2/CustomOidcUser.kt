package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import org.springframework.security.oauth2.core.oidc.user.OidcUser

/** OIDC (Google) 로그인 후 principal 래퍼 — OidcUser를 그대로 위임하면서 memberId/provider 추가 */
class CustomOidcUser(
    private val delegate: OidcUser,
    override val memberId: Long,
    override val provider: String,
) : OidcUser by delegate, AuthenticatedOAuth2User
