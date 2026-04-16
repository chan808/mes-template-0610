package io.github.chan808.authtemplate.common

// RuntimeException 상속으로 트랜잭션 롤백 대상 포함, 도메인별 서브클래스로 핸들러 분기 확보
open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)

class AuthException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, cause)

class MemberException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, cause)

// Retry-After(초)를 담아 GlobalExceptionHandler에서 응답 헤더로 노출
class RateLimitException(val retryAfterSeconds: Long) : BusinessException(ErrorCode.TOO_MANY_REQUESTS)
