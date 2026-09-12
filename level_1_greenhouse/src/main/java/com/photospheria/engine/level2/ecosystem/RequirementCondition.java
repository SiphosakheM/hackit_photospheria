package com.photospheria.engine.level2.ecosystem;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

@JsonDeserialize(using = RequirementConditionDeserializer.class)
public record RequirementCondition(
        String conditionType,
        List<String> interactingSpeciesGroup,
        String comparisonOperator,
        Double requiredThresholdValue,
        String dominanceMode) {

    public RequirementCondition {
        if (conditionType == null || conditionType.isBlank()) {
            throw new IllegalArgumentException("Requirement condition must declare a non-blank condition type.");
        }
        if (interactingSpeciesGroup == null) {
            interactingSpeciesGroup = List.of();
        } else {
            interactingSpeciesGroup = List.copyOf(interactingSpeciesGroup);
        }
    }
}