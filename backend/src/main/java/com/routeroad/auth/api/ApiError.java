package com.routeroad.auth.api;

import java.util.Map;

public record ApiError(Error error) {
    public record Error(String code, String message, Map<String, Object> details) {}
    public static ApiError of(String code, String message) {
        return new ApiError(new Error(code, message, Map.of()));
    }
}
