package com.photospheria.engine.level1.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public record SimulationCommand(
        @JsonProperty("type") String commandType,
        @JsonProperty("tick") int executionTick,
        @JsonProperty("season") String seasonName,
        @JsonProperty("event") String eventName) {

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
        if (eventName != null && eventName.isBlank()) {
            throw new IllegalArgumentException("Simulation command event name cannot be blank when present; received event [" + eventName + "].");
        }
        if ("event".equals(commandType) && (eventName == null || eventName.isBlank())) {
            throw new IllegalArgumentException(
                "Simulation command of type [event] must declare a non-blank event name at execution tick [" + executionTick + "].");
        }
    }

    public Optional<String> seasonNamePresent() {
        return Optional.ofNullable(seasonName);
    }

    public Optional<String> eventNamePresent() {
        return Optional.ofNullable(eventName);
    }
}