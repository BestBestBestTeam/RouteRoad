package com.routeroad.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.auth.application.Account;
import java.util.UUID;

public record RegisteredUser(UUID id, String login, String name, UserView.Role role,
                             @JsonProperty("courier_id") UUID courierId) {
    public static RegisteredUser of(Account account) {
        return new RegisteredUser(account.id(), account.login(), account.name(),
                UserView.Role.valueOf(account.role().name()), account.courierId());
    }
}
