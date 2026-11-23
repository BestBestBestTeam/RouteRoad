package com.routeroad.simulation.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.UUID;

public interface ScenarioStateRepository extends JpaRepository<ScenarioStateEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<ScenarioStateEntity> findByScenarioId(UUID scenarioId);
}
