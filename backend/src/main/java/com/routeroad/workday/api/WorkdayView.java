package com.routeroad.workday.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.offices.api.OfficeView;
import com.routeroad.shared.api.Clock;
import java.time.LocalDate;
import java.util.List;

public record WorkdayView(LocalDate date, WorkdayCounts counts,
                          @JsonProperty("has_routes") boolean hasRoutes,
                          List<OfficeView> offices, Clock clock) {}
