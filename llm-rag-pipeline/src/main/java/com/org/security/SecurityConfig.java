package com.org.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * API-key based security for the REST API (servlet stack), modelled on {@code llm-gateway}:
 * a stateless {@code X-API-Key} check with DB-backed keys, returning {@code 401} JSON on failure.
 *
 * <p>When {@code app.security.auth-enabled=false} (the dev default) every route is permitted, but
 * the security response headers and CORS policy are still applied. When enabled, {@code /api/**}
 * requires a valid key while actuator/health/docs remain open.</p>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final SecurityProperties properties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)));

        // Rate limiting applies whether or not API-key auth is enabled.
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        if (!properties.isAuthEnabled()) {
            log.warn("SECURITY | API-key authentication is DISABLED (app.security.auth-enabled=false)");
            return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }

        log.info("SECURITY | API-key authentication is ENABLED on /api/**");
        return http
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/**",
                                "/swagger-ui.html", "/openapi.yaml", "/favicon.ico").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", properties.getHeader()));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
