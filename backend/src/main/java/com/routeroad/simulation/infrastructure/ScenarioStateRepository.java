package com.routeroad.simulation.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScenarioStateRepository extends JpaRepository<ScenarioStateEntity, UUID> {
}
