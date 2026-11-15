package com.routeroad.planning.infrastructure;

import com.routeroad.planning.domain.PlanningAlgorithm;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PlanningAlgorithmConverter implements AttributeConverter<PlanningAlgorithm, String> {

    @Override
    public String convertToDatabaseColumn(PlanningAlgorithm algorithm) {
        if (algorithm == null) {
            return null;
        }
        return switch (algorithm) {
            case GREEDY -> "greedy";
            case ORTOOLS -> "ortools";
        };
    }

    @Override
    public PlanningAlgorithm convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "greedy" -> PlanningAlgorithm.GREEDY;
            case "ortools" -> PlanningAlgorithm.ORTOOLS;
            default -> throw new IllegalArgumentException("Unknown planning algorithm: " + value);
        };
    }
}
