package io.github.chan808.authtemplate.auth.infrastructure.security

import io.github.chan808.authtemplate.auth.application.port.TokenStore
import io.github.chan808.authtemplate.auth.infrastructure.oauth2.CustomOAuth2UserService
import io.github.chan808.authtemplate.auth.infrastructure.oauth2.CustomOidcUserService
import io.github.chan808.authtemplate.auth.infrastructure.oauth2.LocaleAwareOAuth2AuthorizationRequestResolver
import io.github.chan808.authtemplate.auth.infrastructure.oauth2.OAuth2FailureHandler
import io.github.chan808.authtemplate.auth.infrastructure.oauth2.OAuth2SuccessHandler
import io.github.chan808.authtemplate.member.api.MemberApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val memberApi: MemberApi,
    private val tokenStore: TokenStore,
    private val securityExceptionHandler: SecurityExceptionHandler,
    @Value("\${cors.allowed-origin:http://localhost:3000}") private val allowedOrigin: String,
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
    @Value("\${app.auth.oauth.enabled:true}") private val oauthEnabled: Boolean,
    @Value("\${management.endpoints.web.public.info:false}") private val publicInfoEndpoint: Boolean,
    @Value("\${management.endpoints.web.public.prometheus:false}") private val publicPrometheusEndpoint: Boolean,
) {
    @Autowired(required = false)
    private val clientRegistrationRepository: ClientRegistrationRepository? = null

    @Autowired(required = false)
    private val customOAuth2UserService: CustomOAuth2UserService? = null

    @Autowired(required = false)
    private val customOidcUserService: CustomOidcUserService? = null

    @Autowired(required = false)
    private val oauth2SuccessHandler: OAuth2SuccessHandler? = null

    @Autowired(required = false)
    private val oauth2FailureHandler: OAuth2FailureHandler? = null

    @Autowired(required = false)
    private val oauth2AuthorizationRequestResolver: OAuth2AuthorizationRequestResolver? = null

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers ->
                if (cookieSecure) {
                    headers.httpStrictTransportSecurity { it.maxAgeInSeconds(31536000).includeSubDomains(true) }
                } else {
                    headers.httpStrictTransportSecurity { it.disable() }
                }
                headers.contentSecurityPolicy { it.policyDirectives(CONTENT_SECURITY_POLICY) }
                headers.frameOptions { it.deny() }
                headers.contentTypeOptions { }
                headers.permissionsPolicyHeader { it.policy(PERMISSIONS_POLICY) }
                headers.referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
            }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/**").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/members").permitAll()
                it.requestMatchers("/api-docs/**", "/swagger-ui/**").permitAll()
                if (oauthEnabled) {
                    it.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                }
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                if (publicInfoEndpoint) {
                    it.requestMatchers("/actuator/info").permitAll()
                } else {
                    it.requestMatchers("/actuator/info").hasAuthority("ADMIN")
                }
                if (publicPrometheusEndpoint) {
                    it.requestMatchers("/actuator/prometheus").permitAll()
                } else {
                    it.requestMatchers("/actuator/prometheus").hasAuthority("ADMIN")
                }
                it.anyRequest().authenticated()
            }
            .addFilterBefore(JwtAuthenticationFilter(jwtProvider, memberApi, tokenStore), UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint(securityExceptionHandler)
                it.accessDeniedHandler(securityExceptionHandler)
            }

        if (
            oauthEnabled &&
            clientRegistrationRepository != null &&
            customOAuth2UserService != null &&
            customOidcUserService != null &&
            oauth2SuccessHandler != null &&
            oauth2FailureHandler != null
        ) {
            http.oauth2Login { oauth2 ->
                oauth2.authorizationEndpoint {
                    it.baseUri("/oauth2/authorization")
                    oauth2AuthorizationRequestResolver?.let { resolver ->
                        it.authorizationRequestResolver(resolver)
                    }
                }
                oauth2.redirectionEndpoint { it.baseUri("/login/oauth2/code/*") }
                oauth2.userInfoEndpoint {
                    it.userService(customOAuth2UserService)
                    it.oidcUserService(customOidcUserService)
                }
                oauth2.successHandler(oauth2SuccessHandler)
                oauth2.failureHandler(oauth2FailureHandler)
            }
        }

        return http.build()
    }

    @Bean
    @ConditionalOnBean(ClientRegistrationRepository::class)
    fun oauth2AuthorizationRequestResolver(
        clientRegistrationRepository: ClientRegistrationRepository,
        @Value("\${app.default-locale:ko}") defaultLocale: String,
    ): OAuth2AuthorizationRequestResolver =
        LocaleAwareOAuth2AuthorizationRequestResolver(
            delegate = DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization"),
            defaultLocale = defaultLocale,
        )

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf(allowedOrigin)
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("Authorization", "Content-Type", "X-CSRF-GUARD")
        config.allowCredentials = true
        config.maxAge = 3600L
        return UrlBasedCorsConfigurationSource().also {
            it.registerCorsConfiguration("/**", config)
        }
    }

    companion object {
        private const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data:; " +
                "font-src 'self' data:; " +
                "connect-src 'self'; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "frame-ancestors 'none'"

        private const val PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()"
    }
}
