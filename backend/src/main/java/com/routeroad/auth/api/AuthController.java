package com.routeroad.auth.api;

import com.routeroad.auth.application.AccountService;
import com.routeroad.auth.infrastructure.SessionAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AccountService accounts;
    private final SessionAuthentication sessions;
    public AuthController(AccountService accounts, SessionAuthentication sessions) {
        this.accounts = accounts; this.sessions = sessions;
    }
    @PostMapping("/auth/login")
    public UserView login(@Valid @RequestBody AuthRequests.Login body,
                          HttpServletRequest request, HttpServletResponse response) {
        String token = sessions.login(body.login(), body.password(), request, response);
        return UserView.of(accounts.current(body.login()), token);
    }
    @GetMapping("/me")
    public UserView me(Authentication authentication, HttpServletRequest request) {
        return UserView.of(accounts.current(authentication.getName()), SessionAuthentication.token(request));
    }
    @PatchMapping("/me")
    public UserView rename(@Valid @RequestBody AuthRequests.Rename body, Authentication authentication,
                           HttpServletRequest request) {
        return UserView.of(accounts.rename(authentication.getName(), body.name()), SessionAuthentication.token(request));
    }
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredUser register(@Valid @RequestBody AuthRequests.Register body,
                                   @RequestHeader("X-Scenario-ID") UUID scenarioId) {
        return RegisteredUser.of(accounts.register(scenarioId, body.login(), body.password(), body.name(), body.courierId()));
    }
}
