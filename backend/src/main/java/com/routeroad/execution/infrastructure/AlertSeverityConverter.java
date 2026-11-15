package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertSeverity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AlertSeverityConverter implements AttributeConverter<AlertSeverity, String> {

    @Override
    public String convertToDatabaseColumn(AlertSeverity value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case WARNING -> "warning";
            case CRITICAL -> "critical";
        };
    }

    @Override
    public AlertSeverity convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "warning" -> AlertSeverity.WARNING;
            case "critical" -> AlertSeverity.CRITICAL;
            default -> throw new IllegalArgumentException("Unknown AlertSeverity: " + value);
        };
    }
}
