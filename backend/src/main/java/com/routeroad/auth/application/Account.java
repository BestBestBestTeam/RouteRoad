package com.routeroad.auth.application;

import com.routeroad.shared.domain.AppUserRole;
import java.util.UUID;

/** Public account data; credentials never cross this application boundary. */
public record Account(UUID id, String login, String name, AppUserRole role, UUID courierId) {}
