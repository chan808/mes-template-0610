package io.github.chan808.authtemplate.common.config

import io.github.chan808.authtemplate.common.BusinessException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.RateLimitException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestCookieException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

// Filter layer exceptions from Spring Security are handled separately in AuthenticationEntryPoint / AccessDeniedHandler.
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(RateLimitException::class)
    fun handleRateLimit(ex: RateLimitException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val detail = buildProblemDetail(ex.errorCode, ex.message, request.requestURI)
        return ResponseEntity.status(ex.errorCode.httpStatus)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(detail)
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val detail = buildProblemDetail(ex.errorCode, ex.message, request.requestURI)
        return ResponseEntity.status(ex.errorCode.httpStatus).body(detail)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }
        val detail = buildProblemDetail(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.message, requestUri(request))
        detail.setProperty("errors", fieldErrors)
        return handleExceptionInternal(ex, detail, headers, ErrorCode.INVALID_INPUT.httpStatus, request)
            ?: ResponseEntity.badRequest().body(detail)
    }

    override fun handleServletRequestBindingException(
        ex: ServletRequestBindingException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        if (ex !is MissingRequestHeaderException) {
            return super.handleServletRequestBindingException(ex, headers, status, request)
                ?: ResponseEntity.status(status).build()
        }
        val detail = buildProblemDetail(
            ErrorCode.INVALID_INPUT,
            "Required header is missing: ${ex.headerName}",
            requestUri(request),
        )
        return handleExceptionInternal(ex, detail, headers, ErrorCode.INVALID_INPUT.httpStatus, request)
            ?: ResponseEntity.badRequest().body(detail)
    }

    @ExceptionHandler(MissingRequestCookieException::class)
    fun handleMissingCookie(ex: MissingRequestCookieException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val detail = buildProblemDetail(
            ErrorCode.REFRESH_TOKEN_NOT_FOUND,
            ErrorCode.REFRESH_TOKEN_NOT_FOUND.message,
            request.requestURI,
        )
        return ResponseEntity.status(ErrorCode.REFRESH_TOKEN_NOT_FOUND.httpStatus).body(detail)
    }

    private fun buildProblemDetail(errorCode: ErrorCode, message: String, uri: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(errorCode.httpStatus, message).apply {
            title = errorCode.name
            instance = URI.create(uri)
        }

    private fun requestUri(request: WebRequest): String =
        (request as? ServletWebRequest)?.request?.requestURI ?: "/"
}
