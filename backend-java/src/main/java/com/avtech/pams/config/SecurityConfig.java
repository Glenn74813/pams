package com.avtech.pams.config;

import com.avtech.pams.auth.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                // Stateless bearer-token API: no cookies, so no CSRF surface.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // QR scan pages opened from a phone; every request carries a signed checksum.
                        .requestMatchers(HttpMethod.GET, "/api/public/qr/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> json(res, 401, "{\"status\":\"Authorization Token not found\"}"))
                        .accessDeniedHandler((req, res, ex) -> json(res, 403, "{\"error\":\"You do not have permission to perform this action.\"}")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** The JWT filter runs inside the security chain only, not a second time as a plain servlet filter. */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /** Authentication is JWT-only; this stops Spring Boot creating a default in-memory user. */
    @Bean
    UserDetailsService noFormLoginUsers() {
        return username -> {
            throw new UsernameNotFoundException("Password login goes through /api/auth/login");
        };
    }

    /** Reads Laravel's $2y$ hashes and writes $2y$ so both backends can share the users table. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 10);
    }

    private static void json(jakarta.servlet.http.HttpServletResponse res, int status, String body) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(body);
    }
}
