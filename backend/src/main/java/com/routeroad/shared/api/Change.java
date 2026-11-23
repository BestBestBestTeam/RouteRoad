package com.routeroad.shared.api;

import tools.jackson.databind.JsonNode;

public record Change(String field, JsonNode before, JsonNode after) {}
