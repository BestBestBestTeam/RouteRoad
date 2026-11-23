package com.routeroad.auth.application;

import com.routeroad.shared.domain.AppUserRole;
import java.util.Optional;
import java.util.UUID;

public interface AccountStore {
    Optional<Account> findByLogin(String login);
    Account rename(String login, String name);
    boolean loginExists(String login);
    boolean courierAccountExists(UUID courierId);
    boolean courierExists(UUID courierId);
    boolean lockCurrentScenario(UUID scenarioId);
    Account create(String login, String passwordHash, String name, AppUserRole role, UUID courierId);
}
