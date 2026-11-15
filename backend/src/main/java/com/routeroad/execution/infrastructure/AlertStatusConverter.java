package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AlertStatusConverter implements AttributeConverter<AlertStatus, String> {

    @Override
    public String convertToDatabaseColumn(AlertStatus value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case OPEN -> "open";
            case RESOLVED -> "resolved";
        };
    }

    @Override
    public AlertStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "open" -> AlertStatus.OPEN;
            case "resolved" -> AlertStatus.RESOLVED;
            default -> throw new IllegalArgumentException("Unknown AlertStatus: " + value);
        };
    }
}
