package com.org.llm.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API security for the graph module. Mirrors the pipeline module's security posture:
 * security headers, CORS policy, and Keycloak JWT gating on {@code /api/**}.
 *
 * <p><b>Note:</b> the previous version of this class called {@code .authenticated()} on {@code
 * /api/**} without ever configuring an authentication mechanism, so Spring Security had no way to
 * populate an {@code Authentication} for the request. Wiring {@code oauth2ResourceServer()} below
 * is what actually makes {@code auth-enabled=true} reject unauthenticated callers.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties properties;

    /**
     * Configures the stateless security filter chain: disables session-based auth mechanisms,
     * applies CORS and security headers, and gates {@code /api/**} behind Keycloak JWT auth when
     * {@code app.security.auth-enabled=true}.
     */
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

        if (!properties.isAuthEnabled()) {
            log.warn("GRAPH SECURITY | OAuth2 authentication is DISABLED (app.security.auth-enabled=false)");
            return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }

        log.info("GRAPH SECURITY | OAuth2 (Keycloak) JWT authentication is ENABLED on /api/**");
        return http
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .build();
    }

    /**
     * Builds the CORS policy for {@code /api/**} from {@code app.security.allowed-origins}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins().isEmpty()
                ? List.of() : properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private JwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(this::keycloakAuthoritiesConverter);
        return delegate;
    }

    /**
     * Reads Keycloak's {@code realm_access.roles} claim (e.g. {@code ["gateway-user"]}) and maps
     * each entry to a {@code ROLE_*} {@link GrantedAuthority}, e.g. {@code ROLE_GATEWAY-USER}.
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> keycloakAuthoritiesConverter(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .toList();
    }
}
