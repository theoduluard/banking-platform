package com.solarisbank.analytics_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for analytics-service.
 *
 * All authentication is handled upstream by the API gateway (JwtGatewayFilter).
 * The gateway validates the JWT and injects X-User-Id / X-User-Role before
 * forwarding the request here. This service therefore trusts those headers and
 * does not need to re-validate the original Bearer token.
 *
 * Disabling HTTP Basic prevents Spring Security from emitting a
 * "WWW-Authenticate: Basic" challenge header when it sees an "Authorization: Bearer"
 * header it can't process — which can cause browsers to retry without the header.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
