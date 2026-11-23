package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertOrderStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AlertOrderStatusConverter implements AttributeConverter<AlertOrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(AlertOrderStatus value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case PENDING -> "pending";
            case RESOLVED -> "resolved";
        };
    }

    @Override
    public AlertOrderStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "pending" -> AlertOrderStatus.PENDING;
            case "resolved" -> AlertOrderStatus.RESOLVED;
            default -> throw new IllegalArgumentException("Unknown AlertOrderStatus: " + value);
        };
    }
}
