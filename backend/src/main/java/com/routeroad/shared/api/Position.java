package com.routeroad.shared.api;

import java.time.Instant;

public record Position(double lat, double lon, Instant at) {}
