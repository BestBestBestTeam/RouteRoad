package com.routeroad.shared.infrastructure;

import com.routeroad.shared.domain.AppUserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AppUserRoleConverter implements AttributeConverter<AppUserRole, String> {

    @Override
    public String convertToDatabaseColumn(AppUserRole value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case DISPATCHER -> "dispatcher";
            case REPRESENTATIVE -> "representative";
        };
    }

    @Override
    public AppUserRole convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "dispatcher" -> AppUserRole.DISPATCHER;
            case "representative" -> AppUserRole.REPRESENTATIVE;
            default -> throw new IllegalArgumentException("Unknown AppUserRole: " + value);
        };
    }
}
