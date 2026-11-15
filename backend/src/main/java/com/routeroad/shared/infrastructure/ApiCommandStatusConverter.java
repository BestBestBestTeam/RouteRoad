package com.routeroad.shared.infrastructure;

import com.routeroad.shared.domain.ApiCommandStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ApiCommandStatusConverter implements AttributeConverter<ApiCommandStatus, String> {

    @Override
    public String convertToDatabaseColumn(ApiCommandStatus value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case PENDING -> "pending";
            case RUNNING -> "running";
            case SUCCEEDED -> "succeeded";
            case FAILED -> "failed";
        };
    }

    @Override
    public ApiCommandStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "pending" -> ApiCommandStatus.PENDING;
            case "running" -> ApiCommandStatus.RUNNING;
            case "succeeded" -> ApiCommandStatus.SUCCEEDED;
            case "failed" -> ApiCommandStatus.FAILED;
            default -> throw new IllegalArgumentException("Unknown ApiCommandStatus: " + value);
        };
    }
}
