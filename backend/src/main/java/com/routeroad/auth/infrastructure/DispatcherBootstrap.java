package com.routeroad.auth.infrastructure;

import com.routeroad.auth.application.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "routeroad.bootstrap.dispatcher", name = "enabled", havingValue = "true")
public class DispatcherBootstrap implements ApplicationRunner {
    private final AccountService accounts;
    private final String login;
    private final String password;
    private final String name;
    public DispatcherBootstrap(AccountService accounts,
            @Value("${routeroad.bootstrap.dispatcher.login:}") String login,
            @Value("${routeroad.bootstrap.dispatcher.password:}") String password,
            @Value("${routeroad.bootstrap.dispatcher.name:}") String name) {
        this.accounts = accounts; this.login = login; this.password = password; this.name = name;
    }
    @Override public void run(ApplicationArguments args) { accounts.initializeDispatcher(login, password, name); }
}
