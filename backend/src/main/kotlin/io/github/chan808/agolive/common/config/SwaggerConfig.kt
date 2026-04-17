package io.github.chan808.authtemplate.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig(
    @Value("\${app.name:Application}") private val appName: String,
) {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("$appName API")
                .description(
                    """
                    JWT 기반 인증 시스템 REST API 문서

                    **인증 방법**
                    1. `POST /api/auth/login` 으로 로그인
                    2. 응답의 `data.accessToken` 값을 복사
                    3. 우측 상단 **Authorize** 버튼 클릭 후 입력

                    **Refresh Token**
                    HttpOnly 쿠키로 자동 관리됩니다. 브라우저에서 테스트 시 자동으로 전송됩니다.

                    **에러 응답 형식 (RFC 7807 ProblemDetail)**
                    ```json
                    {
                      "type":     "about:blank",
                      "title":    "INVALID_CREDENTIALS",
                      "status":   401,
                      "detail":   "이메일 또는 비밀번호가 올바르지 않습니다.",
                      "instance": "/api/auth/login"
                    }
                    ```
                    """.trimIndent(),
                )
                .version("1.0.0"),
        )
        .components(
            Components().addSecuritySchemes(
                "BearerAuth",
                SecurityScheme()
                    .name("BearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("로그인 후 반환된 `accessToken`을 입력하세요. 'Bearer ' 접두사는 자동으로 추가됩니다."),
            ),
        )
}
