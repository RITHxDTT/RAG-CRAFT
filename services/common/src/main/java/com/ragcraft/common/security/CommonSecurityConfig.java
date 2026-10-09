package com.ragcraft.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security shared by all services:
 * - /health/** and /api/public/** and webhooks are open,
 * - /api/internal/** needs the internal service token,
 * - /api/admin/** needs the ADMIN role (internal callers allowed),
 * - everything else under /api needs an authenticated user.
 * Individual services can relax rules by exposing their own SecurityFilterChain with a lower @Order.
 */
@Configuration
@EnableConfigurationProperties(RagCraftProperties.class)
public class CommonSecurityConfig {

    @Bean
    public SecurityFilterChain commonSecurityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .exceptionHandling(handler -> handler
                .authenticationEntryPoint((request, response, ex) -> writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Please sign in to continue."))
                .accessDeniedHandler((request, response, ex) -> writeJson(response, HttpServletResponse.SC_FORBIDDEN, "You do not have access to this resource.")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/health/**", "/error").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                .requestMatchers("/api/public/**", "/api/webhooks/**").permitAll()
                .requestMatchers("/api/internal/**").hasRole(JwtAuthFilter.ROLE_INTERNAL)
                .requestMatchers("/api/admin/**").hasAnyRole(UserPrincipal.ROLE_ADMIN, JwtAuthFilter.ROLE_INTERNAL)
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** The filter is added to the security chain above; stop Boot from also registering it as a plain servlet filter. */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static void writeJson(HttpServletResponse response, int status, String detail) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"detail\":\"" + detail + "\"}");
    }
}
