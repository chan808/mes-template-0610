package io.github.chan808.authtemplate.auth.api

/**
 * auth 모듈의 공개 API.
 * 다른 모듈(예: member)이 auth 기능에 접근할 때 이 인터페이스를 통해 호출한다.
 */
interface AuthApi {
    /** 해당 회원의 모든 리프레시 토큰 세션을 무효화한다. */
    fun invalidateAllSessions(memberId: Long)
}
