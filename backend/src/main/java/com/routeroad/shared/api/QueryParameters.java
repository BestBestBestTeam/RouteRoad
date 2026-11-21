package com.routeroad.shared.api;

import java.time.LocalDate;
import java.util.UUID;

public final class QueryParameters {
    private QueryParameters() {}
    public static LocalDate date(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Expected YYYY-MM-DD");
        return LocalDate.parse(value);
    }
    public static UUID uuid(String value) {
        if (value == null) return null;
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Expected UUID");
        return id;
    }
    public static Boolean bool(String value) {
        if (value == null) return null;
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Expected true or false");
        };
    }
    public static int integer(String value, int fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException("Expected integer");
        return Integer.parseInt(value);
    }
}
