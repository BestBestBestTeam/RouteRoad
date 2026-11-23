package com.routeroad.shared.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Page<T>(List<T> items, int total, int page,
                      @JsonProperty("page_size") int pageSize, Clock clock) {}
