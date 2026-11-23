package com.routeroad.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.stereotype.Component;

@Component
public class SessionAuthentication {
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository tokens;
    public SessionAuthentication(AuthenticationManager manager, SecurityContextRepository contexts,
                                 HttpSessionCsrfTokenRepository tokens) {
        this.manager = manager; this.contexts = contexts; this.tokens = tokens;
    }
    public String login(String login, String password, HttpServletRequest request, HttpServletResponse response) {
        var authentication = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login, password));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        // Rotate the pre-authentication CSRF token together with the session identifier.
        var fresh = tokens.generateToken(request);
        tokens.saveToken(fresh, request, response);
        new XorCsrfTokenRequestAttributeHandler().handle(request, response, () -> fresh);
        return token(request);
    }
    public static String token(HttpServletRequest request) {
        return ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
    }
}
