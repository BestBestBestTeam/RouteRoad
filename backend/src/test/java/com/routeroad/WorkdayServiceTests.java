package com.routeroad;

import com.routeroad.simulation.application.ScenarioNotInitializedException;
import com.routeroad.workday.application.WorkdayReader;
import com.routeroad.workday.application.WorkdayService;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkdayServiceTests {
    private final WorkdayReader reader = mock(WorkdayReader.class);
    private final com.routeroad.simulation.application.ScenarioReader scenarioReader = mock(com.routeroad.simulation.application.ScenarioReader.class);
    private final Instant realNow = Instant.parse("2026-11-15T10:00:01.250Z");
    private final Instant modelAnchor = Instant.parse("2026-11-15T23:59:59Z");
    private final LocalDate date = LocalDate.of(2026, 11, 15);
    private final WorkdayService service = new WorkdayService(reader, new com.routeroad.simulation.application.ScenarioClockQuery(scenarioReader, Clock.fixed(realNow, ZoneOffset.UTC)));

    private void scenario(Instant realAnchor, double speed) {
        when(scenarioReader.findCurrent()).thenReturn(Optional.of(new com.routeroad.simulation.application.ScenarioReader.Scenario(UUID.randomUUID(), date,
                modelAnchor, realAnchor, speed, 9_007_199_254_740_993L, java.util.Map.of())));
        when(reader.count(any())).thenReturn(new WorkdayReader.Counts(0, 0, 0, 0, 0, false));
        when(reader.findOffices()).thenReturn(List.of());
    }
    @Test void fractionalSpeedPreservesSubsecondPrecision() {
        scenario(realNow.minusMillis(1250), 0.5);
        assertThat(service.get(null).clock().now()).isEqualTo(modelAnchor.plusMillis(625));
    }
    @Test void pausedClockStaysAtModelAnchor() {
        scenario(realNow.minusSeconds(1000), 0);
        assertThat(service.get(null).clock().now()).isEqualTo(modelAnchor);
    }
    @Test void defaultDateIsPersistedScenarioDayEvenAfterMidnight() {
        scenario(realNow.minusSeconds(2), 1);
        var result = service.get(null);
        assertThat(result.date()).isEqualTo(date);
        assertThat(result.clock().now()).isEqualTo(Instant.parse("2026-11-16T00:00:01Z"));
        verify(reader).count(date);
    }
    @Test void explicitDateDoesNotChangeScenarioClock() {
        scenario(realNow, 2);
        var selected = date.plusDays(1);
        var result = service.get(selected);
        assertThat(result.date()).isEqualTo(selected);
        assertThat(result.clock().now()).isEqualTo(modelAnchor);
        verify(reader).count(selected);
    }
    @Test void realTimeBeforeAnchorUsesTheSameClockFormula() {
        scenario(realNow.plusMillis(250), 2);
        assertThat(service.get(null).clock().now()).isEqualTo(modelAnchor.minusMillis(500));
    }
    @Test void longElapsedIntervalDoesNotOverflowNanosecondDuration() {
        Instant ancient = Instant.parse("1000-01-01T00:00:00Z");
        scenario(ancient, 1);
        var elapsed = java.time.Duration.between(ancient, realNow);
        assertThat(service.get(null).clock().now()).isEqualTo(modelAnchor.plus(elapsed));
    }
    @Test void missingScenarioDoesNotQueryCountsOrInventData() {
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(date)).isInstanceOf(ScenarioNotInitializedException.class);
        verify(reader, never()).count(any());
        verify(reader, never()).findOffices();
    }
}
