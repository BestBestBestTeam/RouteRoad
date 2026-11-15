package com.routeroad.auth.infrastructure;

import com.routeroad.auth.application.Account;
import com.routeroad.auth.application.AccountException;
import com.routeroad.auth.application.AccountStore;
import com.routeroad.couriers.infrastructure.CourierRepository;
import com.routeroad.shared.domain.AppUserRole;
import com.routeroad.shared.infrastructure.AppUserEntity;
import com.routeroad.shared.infrastructure.AppUserRepository;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAccountStore implements AccountStore {
    private final AppUserRepository users;
    private final CourierRepository couriers;
    private final ScenarioStateRepository scenarios;

    public JpaAccountStore(AppUserRepository users, CourierRepository couriers, ScenarioStateRepository scenarios) {
        this.users = users; this.couriers = couriers; this.scenarios = scenarios;
    }
    @Override public Optional<Account> findByLogin(String login) { return users.findByLogin(login).map(this::view); }
    @Override public boolean loginExists(String login) { return users.existsByLogin(login); }
    @Override public boolean courierAccountExists(UUID id) { return users.existsByCourierId(id); }
    @Override public boolean courierExists(UUID id) { return couriers.existsById(id); }
    @Override public boolean lockCurrentScenario(UUID id) { return scenarios.findByScenarioId(id).isPresent(); }
    @Override public Account rename(String login, String name) {
        var user = users.findByLogin(login).orElseThrow(() -> new AccountException(
                AccountException.Kind.UNAUTHENTICATED, "Требуется вход в систему"));
        user.rename(name);
        return view(user);
    }
    @Override public Account create(String login, String passwordHash, String name, AppUserRole role, UUID courierId) {
        try {
            return view(users.saveAndFlush(new AppUserEntity(UUID.randomUUID(), login, passwordHash, name, role, courierId)));
        } catch (DataIntegrityViolationException exception) {
            // Only the two account uniqueness constraints are registration conflicts.
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && ("app_user_login_key".equals(violation.getConstraintName())
                        || "app_user_courier_key".equals(violation.getConstraintName()))) {
                    throw new AccountException(AccountException.Kind.CONFLICT,
                            "Логин или представитель уже связан с аккаунтом");
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }
    private Account view(AppUserEntity user) {
        return new Account(user.getId(), user.getLogin(), user.getName(), user.getRole(), user.getCourierId());
    }
}
