package com.routeroad.planning.infrastructure;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteStopRepository extends Repository<RouteStopEntity, RouteStopId> {

    Optional<RouteStopEntity> findById(RouteStopId id);

    List<RouteStopEntity> findById_RouteIdOrderById_SequenceAsc(UUID routeId);
}
