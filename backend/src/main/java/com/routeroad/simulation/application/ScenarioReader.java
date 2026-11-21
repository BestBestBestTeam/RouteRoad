package com.routeroad.simulation.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioReader {
    Optional<Scenario> findCurrent();
    record Scenario(UUID id, LocalDate serviceDate, Instant modelAnchorAt,
                    Instant realAnchorAt, double speed, Long revision) {}
}
