package com.routeroad.shared.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record Clock(@JsonProperty("scenario_id") UUID scenarioId, Instant now, double speed,
                    @JsonFormat(shape = JsonFormat.Shape.STRING) Long revision) {}
