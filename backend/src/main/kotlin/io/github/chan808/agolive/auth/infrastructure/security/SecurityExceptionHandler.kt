package io.github.chan808.authtemplate.auth.infrastructure.security

import io.github.chan808.authtemplate.common.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class SecurityExceptionHandler(private val objectMapper: ObjectMapper) : AuthenticationEntryPoint, AccessDeniedHandler {

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, ex: AuthenticationException) {
        val errorCode = request.getAttribute("jwt-error") as? ErrorCode ?: ErrorCode.UNAUTHENTICATED
        writeProblemDetail(response, errorCode)
    }

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, ex: AccessDeniedException) {
        writeProblemDetail(response, ErrorCode.ACCESS_DENIED)
    }

    private fun writeProblemDetail(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.httpStatus.value()
        response.contentType = "application/problem+json;charset=UTF-8"
        objectMapper.writeValue(
            response.writer,
            mapOf("status" to errorCode.httpStatus.value(), "title" to errorCode.name, "detail" to errorCode.message),
        )
    }
}
