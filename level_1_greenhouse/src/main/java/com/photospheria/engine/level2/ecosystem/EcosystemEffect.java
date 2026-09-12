package com.photospheria.engine.level2.ecosystem;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EcosystemEffect(
        @JsonProperty("type") String effectType,
        @JsonProperty("target") String effectTargetName,
        @JsonProperty("value") double effectMultiplierValue,
        @JsonProperty("mode") String effectApplicationMode) {

    public EcosystemEffect {
        if (effectType == null || effectType.isBlank()) {
            throw new IllegalArgumentException("Ecosystem effect must declare a non-blank effect type.");
        }
        if (effectTargetName == null || effectTargetName.isBlank()) {
            throw new IllegalArgumentException(
                "Ecosystem effect of type [" + effectType + "] must declare a non-blank effect target name.");
        }
        if (effectApplicationMode == null || effectApplicationMode.isBlank()) {
            throw new IllegalArgumentException(
                "Ecosystem effect of type [" + effectType + "] must declare a non-blank effect application mode.");
        }
    }
}