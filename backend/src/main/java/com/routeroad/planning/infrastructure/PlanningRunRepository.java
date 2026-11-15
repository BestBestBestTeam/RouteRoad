package com.routeroad.planning.infrastructure;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface PlanningRunRepository extends Repository<PlanningRunEntity, Long> {

    Optional<PlanningRunEntity> findById(Long planVersion);

    Optional<PlanningRunEntity> findByRequestId(UUID requestId);
}
