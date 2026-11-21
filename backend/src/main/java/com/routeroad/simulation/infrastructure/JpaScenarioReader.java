package com.routeroad.simulation.infrastructure;

import com.routeroad.simulation.application.ScenarioReader;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class JpaScenarioReader implements ScenarioReader {
    private final ScenarioStateRepository scenarios;
    public JpaScenarioReader(ScenarioStateRepository scenarios) { this.scenarios = scenarios; }
    @Override public Optional<Scenario> findCurrent() {
        return scenarios.findAll().stream().findFirst().map(state -> new Scenario(state.getScenarioId(),
                state.getServiceDate(), state.getModelAnchorAt(), state.getRealAnchorAt(),
                state.getSpeed(), state.getRevision()));
    }
}
