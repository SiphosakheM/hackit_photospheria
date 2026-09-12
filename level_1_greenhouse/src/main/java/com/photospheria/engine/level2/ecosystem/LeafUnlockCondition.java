package com.photospheria.engine.level2.ecosystem;

public record LeafUnlockCondition(
        String conditionType,
        String interactingSpeciesName,
        String interactingPlantName,
        String interactingFeatureName,
        String interactingEventName,
        String comparisonOperator,
        Double requiredThresholdValue) implements UnlockConditionNode {

    public LeafUnlockCondition {
        if (conditionType == null || conditionType.isBlank()) {
            throw new IllegalArgumentException("Leaf unlock condition must declare a non-blank condition type.");
        }
    }
}