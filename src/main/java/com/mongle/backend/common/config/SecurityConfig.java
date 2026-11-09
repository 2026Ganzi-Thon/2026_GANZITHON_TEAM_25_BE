package com.mongle.backend.common.config;

import com.mongle.backend.common.security.JwtAuthenticationFilter;
import com.mongle.backend.common.security.JwtTokenProvider;
import com.mongle.backend.common.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 토큰은 Bearer 헤더로만 주고받는다(쿠키 없음) — 프런트가 웹이든 네이티브 앱이든 이 설정은
 * 바뀌지 않는다. 웹 프런트가 붙을 때 origin만 ALLOWED_ORIGINS 환경변수로 넣어주면 된다
 * (API_명세서_v1 9장 "프론트엔드 스택 미정" 블로커에 대한 답: 백엔드는 스택을 몰라도 된다).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/auth/anonymous",
            "/actuator/health",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtTokenProvider jwtTokenProvider,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            @Value("${mongle.cors.allowed-origins:*}") List<String> allowedOrigins) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 쿠키 세션이 아니라 Bearer 토큰이라 CSRF 대상이 아님
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
