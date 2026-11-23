package com.routeroad.auth.application;

import com.routeroad.shared.domain.AppUserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import static com.routeroad.auth.application.AccountException.Kind.*;

@Service
public class AccountService {
    private final AccountStore accounts;
    private final PasswordEncoder passwords;

    public AccountService(AccountStore accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public Account current(String login) {
        return accounts.findByLogin(login).orElseThrow(() ->
                new AccountException(UNAUTHENTICATED, "Требуется вход в систему"));
    }

    @Transactional
    public Account rename(String login, String name) {
        return accounts.rename(login, validName(name));
    }

    @Transactional
    public Account register(UUID scenarioId, String login, String password, String name, UUID courierId) {
        if (!accounts.lockCurrentScenario(scenarioId)) {
            throw new AccountException(SCENARIO_RESET, "Сценарий отсутствует или изменился");
        }
        if (!accounts.courierExists(courierId)) {
            throw new AccountException(NOT_FOUND, "Представитель не найден");
        }
        if (accounts.loginExists(login) || accounts.courierAccountExists(courierId)) {
            throw new AccountException(CONFLICT, "Логин или представитель уже связан с аккаунтом");
        }
        return accounts.create(login, passwords.encode(password), validName(name),
                AppUserRole.REPRESENTATIVE, courierId);
    }

    @Transactional
    public void initializeDispatcher(String login, String password, String name) {
        if (login == null || login.isBlank() || login.length() > 256 || password == null
                || password.isBlank() || password.length() > 1024) {
            throw new IllegalStateException("Dispatcher bootstrap requires explicit valid login and password");
        }
        var existing = accounts.findByLogin(login);
        if (existing.isPresent()) {
            if (existing.get().role() != AppUserRole.DISPATCHER) {
                throw new IllegalStateException("Dispatcher bootstrap login is already occupied");
            }
            return; // Never overwrite an existing account or its credentials.
        }
        accounts.create(login, passwords.encode(password), validName(name), AppUserRole.DISPATCHER, null);
    }

    private String validName(String name) {
        if (name == null || name.length() < 2 || name.length() > 64 || name.strip().length() < 2) {
            throw new AccountException(INVALID, "Имя должно содержать от 2 до 64 символов");
        }
        return name;
    }
}
