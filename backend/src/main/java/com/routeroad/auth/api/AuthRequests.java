package com.routeroad.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthRequests {
    private AuthRequests() {}

    public record Login(@NotBlank @Size(max = 256) String login,
                        @NotBlank @Size(max = 1024) String password) {
        @Override public String toString() { return "Login[redacted]"; }
    }

    public record Register(@NotBlank @Size(max = 256) String login,
                           @NotBlank @Size(max = 1024) String password,
                           @NotBlank @Size(min = 2, max = 64) String name,
                           @JsonProperty("courier_id") @NotNull UUID courierId) {
        @Override public String toString() { return "Register[redacted]"; }
    }

    public record Rename(@NotBlank @Size(min = 2, max = 64) String name) {}
}
