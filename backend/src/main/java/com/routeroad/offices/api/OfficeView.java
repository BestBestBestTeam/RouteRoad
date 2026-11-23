package com.routeroad.offices.api;

import java.util.UUID;

public record OfficeView(UUID id, String name, String address, double lat, double lon) {}
