package com.photospheria.engine.level1.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public record SimulationCommand(
        @JsonProperty("type") String commandType,
        @JsonProperty("tick") int executionTick,
        @JsonProperty("season") String seasonName) {

    public SimulationCommand {
        if (commandType == null || commandType.isBlank()) {
            throw new IllegalArgumentException("Simulation command type cannot be null or blank; received type [" + commandType + "].");
        }
        if (executionTick < 0) {
            throw new IllegalArgumentException("Simulation command execution tick cannot be negative; received tick [" + executionTick + "].");
        }
        if (seasonName != null && seasonName.isBlank()) {
            throw new IllegalArgumentException("Simulation command season name cannot be blank when present; received season [" + seasonName + "].");
        }
    }

    public Optional<String> seasonNamePresent() {
        return Optional.ofNullable(seasonName);
    }
}