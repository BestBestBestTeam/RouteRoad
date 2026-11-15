package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertKind;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AlertKindConverter implements AttributeConverter<AlertKind, String> {

    @Override
    public String convertToDatabaseColumn(AlertKind value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case DELAY -> "delay";
            case REFUSAL -> "refusal";
            case CONFLICT -> "conflict";
        };
    }

    @Override
    public AlertKind convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "delay" -> AlertKind.DELAY;
            case "refusal" -> AlertKind.REFUSAL;
            case "conflict" -> AlertKind.CONFLICT;
            default -> throw new IllegalArgumentException("Unknown AlertKind: " + value);
        };
    }
}
