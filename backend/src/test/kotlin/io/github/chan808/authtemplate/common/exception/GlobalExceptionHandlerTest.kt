package io.github.chan808.authtemplate.common.exception

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.config.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `business exception maps to error code status and message`() {
        val request = mockk<HttpServletRequest> { every { requestURI } returns "/api/members/me" }

        val response = handler.handleBusinessException(MemberException(ErrorCode.MEMBER_NOT_FOUND), request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MEMBER_NOT_FOUND.message, response.body?.detail)
        assertEquals(ErrorCode.MEMBER_NOT_FOUND.name, response.body?.title)
    }

    @Test
    fun `business exception keeps explicit message`() {
        val request = mockk<HttpServletRequest> { every { requestURI } returns "/api/auth/login" }

        val response = handler.handleBusinessException(
            MemberException(ErrorCode.MEMBER_NOT_FOUND, "찾을 수 없는 회원입니다."),
            request,
        )

        assertEquals("찾을 수 없는 회원입니다.", response.body?.detail)
    }
}
