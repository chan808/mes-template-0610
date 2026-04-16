package io.github.chan808.authtemplate.common

// 로그에서 개인정보 최소화: user@example.com → us***@example.com
// \p{Cntrl}: \n·\r 등 제어 문자 제거 — 공격자가 입력값에 개행을 섞어 가짜 로그를 삽입하는 Log Forging 방어
fun maskEmail(email: String): String {
    val sanitized = email.replace(Regex("\\p{Cntrl}"), "")
    val atIndex = sanitized.indexOf('@')
    if (atIndex <= 0) return "***"
    return "${sanitized.take(2)}***${sanitized.substring(atIndex)}"
}
