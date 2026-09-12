package com.photospheria.engine.level2.ecosystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AnimalSpecies(
        @JsonProperty("id") String animalIdentifier,
        @JsonProperty("name") String animalDisplayName,
        @JsonProperty("requirements") RequirementGroup speciesRequirementGroup,
        @JsonProperty("effects") List<EcosystemEffect> speciesEcosystemEffects) {

    public AnimalSpecies {
        if (animalIdentifier == null || animalIdentifier.isBlank()) {
            throw new IllegalArgumentException("Bestiary animal species must declare a non-blank animal identifier.");
        }
        if (animalDisplayName == null || animalDisplayName.isBlank()) {
            throw new IllegalArgumentException(
                "Bestiary animal species with identifier [" + animalIdentifier + "] must declare a non-blank animal display name.");
        }
        if (speciesRequirementGroup == null) {
            throw new IllegalArgumentException(
                "Bestiary animal species with identifier [" + animalIdentifier + "] must declare a species requirement group.");
        }
        if (speciesEcosystemEffects == null) {
            speciesEcosystemEffects = List.of();
        } else {
            speciesEcosystemEffects = List.copyOf(speciesEcosystemEffects);
        }
    }
}