package com.routeroad.auth.infrastructure;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository tokens, JsonSecurityErrors errors,
            @Value("${routeroad.security.allowed-origins:}") String allowedOrigins) throws Exception {
        Set<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::strip)
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(config -> config.securityContextRepository(contexts))
                .csrf(config -> config.csrfTokenRepository(tokens))
                .authorizeHttpRequests(config -> config
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/health", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/register").hasRole("DISPATCHER")
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, exception) -> {
                            if ("GET".equals(request.getMethod()) && (request.getContextPath() + "/api/me").equals(request.getRequestURI())) {
                                response.setHeader("X-CSRF-Token", SessionAuthentication.token(request));
                            }
                            errors.write(response, 401, "UNAUTHENTICATED", "Требуется вход в систему");
                        })
                        .accessDeniedHandler((request, response, exception) -> errors.write(response, 403,
                                exception instanceof org.springframework.security.web.csrf.CsrfException
                                        ? "INVALID_CSRF" : "FORBIDDEN", "Доступ запрещён")))
                .logout(config -> config.logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .addFilterBefore(new LoginOriginFilter(origins, errors), CsrfFilter.class);
        return http.build();
    }
}
