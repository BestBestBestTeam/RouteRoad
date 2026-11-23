package com.routeroad.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.auth.application.Account;
import java.util.UUID;

public record UserView(UUID id, String name, Role role,
                       @JsonProperty("courier_id") UUID courierId,
                       @JsonProperty("csrf_token") String csrfToken) {
    public static UserView of(Account account, String token) {
        return new UserView(account.id(), account.name(), Role.valueOf(account.role().name()),
                account.courierId(), token);
    }

    public enum Role {
        @JsonProperty("dispatcher") DISPATCHER,
        @JsonProperty("representative") REPRESENTATIVE
    }
}
