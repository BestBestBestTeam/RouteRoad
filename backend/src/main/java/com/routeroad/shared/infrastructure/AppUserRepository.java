package com.routeroad.shared.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByLogin(String login);

    Optional<AppUserEntity> findByCourierId(UUID courierId);

    boolean existsByLogin(String login);

    boolean existsByCourierId(UUID courierId);
}
