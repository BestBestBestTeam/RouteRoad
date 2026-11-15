package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertScope;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AlertScopeConverter implements AttributeConverter<AlertScope, String> {

    @Override
    public String convertToDatabaseColumn(AlertScope value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case ORDER -> "order";
            case COURIER_DAY -> "courier_day";
        };
    }

    @Override
    public AlertScope convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "order" -> AlertScope.ORDER;
            case "courier_day" -> AlertScope.COURIER_DAY;
            default -> throw new IllegalArgumentException("Unknown AlertScope: " + value);
        };
    }
}
