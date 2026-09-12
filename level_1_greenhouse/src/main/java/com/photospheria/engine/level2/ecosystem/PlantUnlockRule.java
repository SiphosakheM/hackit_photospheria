package com.photospheria.engine.level2.ecosystem;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlantUnlockRule(
        @JsonProperty("plant") String plantName,
        @JsonProperty("unlock") UnlockConditionNode unlockConditionNode) {

    public PlantUnlockRule {
        if (plantName == null || plantName.isBlank()) {
            throw new IllegalArgumentException("Plant unlock rule must declare a non-blank plant name.");
        }
        if (unlockConditionNode == null) {
            throw new IllegalArgumentException("Plant unlock rule for plant name [" + plantName + "] must declare an unlock condition node.");
        }
    }
}