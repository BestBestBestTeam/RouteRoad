package com.routeroad.simulation.application;

import com.routeroad.shared.api.Clock;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/** Shared read-only date/clock calculation; callers define the read transaction. */
@Component
public class ScenarioClockQuery {
    private final ScenarioReader reader;
    private final java.time.Clock realClock;
    public ScenarioClockQuery(ScenarioReader reader, java.time.Clock realClock) {
        this.reader = reader;
        this.realClock = realClock;
    }
    public record Context(LocalDate date, LocalDate scenarioDate, Clock clock) {}
    public Optional<Context> get(LocalDate requestedDate) {
        return reader.findCurrent().map(state -> {
            var elapsed = Duration.between(state.realAnchorAt(), realClock.instant());
            var seconds = BigDecimal.valueOf(elapsed.getSeconds()).add(BigDecimal.valueOf(elapsed.getNano(), 9));
            var scaled = seconds.multiply(BigDecimal.valueOf(state.speed())).setScale(9, RoundingMode.DOWN);
            var now = state.modelAnchorAt().plusSeconds(scaled.toBigInteger().longValueExact())
                    .plusNanos(scaled.remainder(BigDecimal.ONE).movePointRight(9).longValueExact());
            return new Context(requestedDate == null ? state.serviceDate() : requestedDate, state.serviceDate(),
                    new Clock(state.id(), now, state.speed(), state.revision()));
        });
    }
}
