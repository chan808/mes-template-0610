package io.github.chan808.authtemplate.auth.infrastructure.oauth2

/**
 * 각 OAuth 제공자의 userInfo 응답 구조가 다르므로 추상화
 * - Google: attributes 최상위에 id, email
 * - Naver:  attributes.response 안에 id, email
 * - Kakao:  attributes.kakao_account.email, attributes.id
 */
sealed class OAuth2UserInfo(protected val attributes: Map<String, Any>) {
    abstract val providerId: String
    abstract val email: String
    abstract val provider: String
}

class GoogleOAuth2UserInfo(attributes: Map<String, Any>) : OAuth2UserInfo(attributes) {
    override val provider = "GOOGLE"
    override val providerId: String get() = attributes["sub"] as String
    override val email: String get() = attributes["email"] as String
}

@Suppress("UNCHECKED_CAST")
class NaverOAuth2UserInfo(attributes: Map<String, Any>) : OAuth2UserInfo(attributes) {
    override val provider = "NAVER"
    private val response: Map<String, Any> get() = attributes["response"] as Map<String, Any>
    override val providerId: String get() = response["id"] as String
    override val email: String get() = response["email"] as String
}

@Suppress("UNCHECKED_CAST")
class KakaoOAuth2UserInfo(attributes: Map<String, Any>) : OAuth2UserInfo(attributes) {
    override val provider = "KAKAO"
    override val providerId: String get() = attributes["id"].toString()
    private val account: Map<String, Any> get() = attributes["kakao_account"] as Map<String, Any>
    override val email: String get() = account["email"] as String
}

fun oAuth2UserInfoOf(registrationId: String, attributes: Map<String, Any>): OAuth2UserInfo =
    when (registrationId.lowercase()) {
        "google" -> GoogleOAuth2UserInfo(attributes)
        "naver"  -> NaverOAuth2UserInfo(attributes)
        "kakao"  -> KakaoOAuth2UserInfo(attributes)
        else -> throw IllegalArgumentException("지원하지 않는 OAuth2 제공자: $registrationId")
    }
