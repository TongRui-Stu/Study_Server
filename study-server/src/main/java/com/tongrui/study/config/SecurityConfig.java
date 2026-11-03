package com.tongrui.study.config;

import com.tongrui.study.mapper.UserMapper;
import com.tongrui.study.security.JwtAuthenticationFilter;
import com.tongrui.study.security.SecurityResponseWriter;
import com.tongrui.study.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtUtil jwtUtil,
                                                   UserMapper userMapper, SecurityResponseWriter responseWriter,
                                                   @Value("${app.web.enabled:false}") boolean serveFrontend) {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    if (serveFrontend) {
                        authorize.requestMatchers(HttpMethod.GET, FrontendController.PAGE_ROUTES).permitAll()
                                .requestMatchers(HttpMethod.GET, "/index.html", "/assets/**", "/favicon.ico",
                                        "/favicon.svg", "/icons.svg").permitAll();
                    }
                    authorize
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                responseWriter.write(response, 401, "未登录或登录已过期"))
                        .accessDeniedHandler((request, response, exception) ->
                                responseWriter.write(response, 403, "没有访问权限")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, userMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
