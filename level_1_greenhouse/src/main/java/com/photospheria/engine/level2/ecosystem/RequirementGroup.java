package com.photospheria.engine.level2.ecosystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RequirementGroup(
        @JsonProperty("type") String logicalOperatorType,
        @JsonProperty("conditions") List<RequirementCondition> childRequirementConditions) {

    public RequirementGroup {
        if (logicalOperatorType == null || logicalOperatorType.isBlank()) {
            throw new IllegalArgumentException("Requirement group must declare a non-blank logical operator type.");
        }
        if (childRequirementConditions == null || childRequirementConditions.isEmpty()) {
            throw new IllegalArgumentException(
                "Requirement group with logical operator type [" + logicalOperatorType
                    + "] must declare at least one child requirement condition.");
        }
        childRequirementConditions = List.copyOf(childRequirementConditions);
    }
}