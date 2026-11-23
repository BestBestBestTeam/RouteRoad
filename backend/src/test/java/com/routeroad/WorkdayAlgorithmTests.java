package com.routeroad;

import com.routeroad.simulation.application.ScenarioClockQuery;
import com.routeroad.simulation.infrastructure.JpaScenarioReader;
import com.routeroad.simulation.infrastructure.ScenarioStateEntity;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import com.routeroad.workday.application.WorkdayReader;
import com.routeroad.workday.application.WorkdayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkdayAlgorithmTests {
    private static final UUID ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 11, 21);
    private static final Instant NOW = Instant.parse("2026-11-21T09:00:00Z");
    private final ScenarioStateRepository states = mock(ScenarioStateRepository.class);
    private final WorkdayReader reader = mock(WorkdayReader.class);
    private final WorkdayService service = new WorkdayService(reader, new ScenarioClockQuery(
            new JpaScenarioReader(states), java.time.Clock.fixed(NOW, ZoneOffset.UTC)));
    private void config(Map<String, Object> config) {
        when(states.findAll()).thenReturn(List.of(new ScenarioStateEntity(ID, 1, config, DATE,
                NOW.minusSeconds(10), NOW.minusSeconds(5), 2.0, 9_007_199_254_740_993L)));
    }
    @BeforeEach void setup() {
        when(reader.count(DATE)).thenReturn(new WorkdayReader.Counts(10, 7, 2, 5, 3, true));
        when(reader.findOffices()).thenReturn(List.of(new WorkdayReader.Office(UUID.randomUUID(), "Office", "Address", 55, 82)));
    }
    @ParameterizedTest @ValueSource(strings = {"greedy", "ortools"})
    void configuredAlgorithmIsReadFromTheSameScenarioAsClock(String algorithm) {
        config(Map.of("algorithm", algorithm));
        var result = service.get(null);
        assertThat(result.algorithm()).isEqualTo(algorithm);
        assertThat(result.clock().scenarioId()).isEqualTo(ID);
        assertThat(result.clock().now()).isEqualTo(NOW);
        assertThat(result.clock().revision()).isEqualTo(9_007_199_254_740_993L);
        verify(states, times(1)).findAll();
    }
    @Test void absentAlgorithmDefaultsToGreedyWithoutChangingStoredConfig() {
        var config = new HashMap<String, Object>();
        config.put("another_setting", "preserved");
        config(config);
        assertThat(service.get(null).algorithm()).isEqualTo("greedy");
        assertThat(config).containsExactlyEntriesOf(Map.of("another_setting", "preserved"));
    }
    @ParameterizedTest @ValueSource(strings = {"null", "1", "true", "[]", "{}", "\"GREEDY\"", "\"\"", "\"unknown\"", "\" ortools \""})
    void invalidPersistedAlgorithmIsAServerErrorBeforeReadingCounts(String value) {
        config(Collections.singletonMap("algorithm", JsonMapper.builder().build().readValue(value, Object.class)));
        assertThatThrownBy(() -> service.get(null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid scenario algorithm configuration");
        verifyNoInteractions(reader);
    }
    @Test void changingAlgorithmKeepsAllOtherResponseFieldsIdentical() {
        config(Map.of("algorithm", "greedy"));
        var before = service.get(null);
        config(Map.of("algorithm", "ortools"));
        var after = service.get(null);
        assertThat(after.algorithm()).isEqualTo("ortools");
        assertThat(after).usingRecursiveComparison().ignoringFields("algorithm").isEqualTo(before);
    }
}
