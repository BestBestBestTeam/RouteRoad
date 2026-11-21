package com.routeroad.shared.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record Actor(UUID id, String name, Role role) {
    public enum Role {
        @JsonProperty("dispatcher") DISPATCHER,
        @JsonProperty("representative") REPRESENTATIVE,
        @JsonProperty("system") SYSTEM
    }
}
