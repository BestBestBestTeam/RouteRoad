package com.routeroad.shared.api;

import java.time.Instant;

public record Resolution(Instant at, Actor actor, String reason) {}
