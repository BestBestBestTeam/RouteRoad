package com.routeroad;

import com.routeroad.simulation.application.*;
import com.routeroad.workday.application.WorkdayMapReader;
import com.routeroad.workday.application.WorkdayMapService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkdayMapServiceTests {
    private final WorkdayMapReader reader = mock(WorkdayMapReader.class);
    private final ScenarioReader scenarios = mock(ScenarioReader.class);
    private final LocalDate day = LocalDate.of(2026,11,21);
    private final Instant now = Instant.parse("2026-11-21T09:00:02Z");
    private final UUID scenarioId = UUID.randomUUID();
    private final WorkdayMapService service = new WorkdayMapService(reader,
            new ScenarioClockQuery(scenarios, java.time.Clock.fixed(now, ZoneOffset.UTC)));
    private void scenario() {
        when(scenarios.findCurrent()).thenReturn(Optional.of(new ScenarioReader.Scenario(scenarioId,day,
                Instant.parse("2026-11-21T23:59:59Z"), now.minusSeconds(2),2,9_007_199_254_740_993L, java.util.Map.of())));
        when(reader.read(any())).thenReturn(new WorkdayMapReader.Result(List.of(),List.of(),List.of(),List.of(),List.of()));
    }
    @Test void emptyMapUsesScenarioDayEvenIfModelClockPassedMidnight() {
        scenario();
        var result = service.get(null);
        verify(reader).read(day);
        assertThat(result.orders()).isEmpty(); assertThat(result.couriers()).isEmpty();
        assertThat(result.offices()).isEmpty(); assertThat(result.routes()).isEmpty(); assertThat(result.alerts()).isEmpty();
        assertThat(result.clock().scenarioId()).isEqualTo(scenarioId);
        assertThat(result.clock().now()).isEqualTo(Instant.parse("2026-11-22T00:00:03Z"));
    }
    @Test void explicitDateDoesNotChangeModelClock() {
        scenario();
        var result = service.get(day.plusDays(1));
        verify(reader).read(day.plusDays(1));
        assertThat(result.clock().now()).isEqualTo(Instant.parse("2026-11-22T00:00:03Z"));
    }
    @Test void missingScenarioDoesNotReadMapOrCreateState() {
        when(scenarios.findCurrent()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(null)).isInstanceOf(ScenarioNotInitializedException.class);
        verifyNoInteractions(reader);
    }
    @Test void mapIsReadInOneRepeatableReadReadOnlyTransaction() throws Exception {
        var transaction = WorkdayMapService.class.getMethod("get",LocalDate.class).getAnnotation(Transactional.class);
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
