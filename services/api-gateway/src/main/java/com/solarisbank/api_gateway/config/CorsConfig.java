package com.solarisbank.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Fix 15: CORS configuration with credentials support.
 * Browsers enforce the "credentials" flag: when a request carries cookies
 * (withCredentials: true in Axios), the server MUST respond with
 * Access-Control-Allow-Origin set to the exact requesting origin (not '*')
 * AND Access-Control-Allow-Credentials: true.
 * Without this, the browser refuses to expose the Set-Cookie response header
 * that carries the HttpOnly refresh token, making cookie-based refresh impossible.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(frontendUrl)
                .allowCredentials(true)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                // Expose Set-Cookie so the browser can process the refresh-token cookie
                .exposedHeaders("Set-Cookie")
                .maxAge(3600);
    }
}
