package com.routeroad.shared.api;

import java.time.Instant;

public record Position(double lat, double lon, Instant at) {
    public static Position fromNullable(Double lat, Double lon, Instant at) {
        return at == null ? null : new Position(lat, lon, at);
    }
}
