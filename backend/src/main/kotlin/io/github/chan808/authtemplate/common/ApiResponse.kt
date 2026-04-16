package io.github.chan808.authtemplate.common

// 성공/실패 역직렬화 스키마 분리: 실패는 ProblemDetail(RFC 7807)로 처리
data class ApiResponse<T>(
    val data: T? = null,
    val message: String? = null,
) {
    companion object {
        fun <T> of(data: T): ApiResponse<T> = ApiResponse(data = data)
        fun <T> of(data: T, message: String): ApiResponse<T> = ApiResponse(data = data, message = message)
        fun success(message: String = "처리가 완료되었습니다."): ApiResponse<Unit> = ApiResponse(message = message)
    }
}
