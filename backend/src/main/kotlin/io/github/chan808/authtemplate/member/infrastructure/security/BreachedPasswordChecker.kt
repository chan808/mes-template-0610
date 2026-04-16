package io.github.chan808.authtemplate.member.infrastructure.security

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.security.MessageDigest
import java.net.http.HttpClient
import java.time.Duration

@Component
class BreachedPasswordChecker(
    @Value("\${app.name:application}") private val serviceName: String,
    @Value("\${app.security.password.hibp.enabled:true}") private val hibpEnabled: Boolean,
) {
    private val restClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build(),
            ).apply {
                setReadTimeout(Duration.ofSeconds(2))
            },
        )
        .build()

    private val log = LoggerFactory.getLogger(BreachedPasswordChecker::class.java)

    fun check(password: String, email: String? = null) {
        checkContextSpecific(password, email)
        if (!hibpEnabled) {
            return
        }
        checkHibp(password)
    }

    private fun checkContextSpecific(password: String, email: String?) {
        val lower = password.lowercase()

        if (lower.contains(serviceName.lowercase())) {
            throw MemberException(ErrorCode.BREACHED_PASSWORD, "비밀번호에 서비스 이름을 포함할 수 없습니다.")
        }

        if (email != null) {
            val localPart = email.substringBefore('@').lowercase()
            if (localPart.length >= 3 && lower.contains(localPart)) {
                throw MemberException(ErrorCode.BREACHED_PASSWORD, "비밀번호에 이메일 주소 일부를 포함할 수 없습니다.")
            }
        }
    }

    private fun checkHibp(password: String) {
        val sha1 = sha1Hex(password).uppercase()
        val prefix = sha1.take(5)
        val suffix = sha1.drop(5)

        try {
            val body = restClient.get()
                .uri("https://api.pwnedpasswords.com/range/$prefix")
                .header("Add-Padding", "true")
                .retrieve()
                .body(String::class.java)
                ?: return

            if (body.lines().any { it.substringBefore(':').equals(suffix, ignoreCase = true) }) {
                throw MemberException(ErrorCode.BREACHED_PASSWORD)
            }
        } catch (ex: MemberException) {
            throw ex
        } catch (ex: Exception) {
            // fail-open: 외부 의존성 장애 때문에 회원가입/비밀번호 변경 전체가 막히지 않도록 둔다.
            log.warn("HIBP password check skipped: {}", ex.message)
        }
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
