package com.routeroad.auth.infrastructure;

import com.routeroad.shared.infrastructure.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class SecurityBeans {
    @Bean PasswordEncoder passwordEncoder() {
        String id = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(id, Map.of(id, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
    @Bean AuthenticationManager authenticationManager(AppUserRepository users, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(login -> users.findByLogin(login)
                .map(user -> User.withUsername(user.getLogin()).password(user.getPasswordHash())
                        .roles(user.getRole().name()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials")));
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean HttpSessionCsrfTokenRepository csrfTokenRepository() {
        var repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-Token");
        return repository;
    }
}
