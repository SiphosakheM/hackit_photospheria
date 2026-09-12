package com.photospheria.engine.level1.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LevelState(
        @JsonProperty("animals_enabled") boolean animalsEnabled,
        @JsonProperty("rows") int rowCount,
        @JsonProperty("cols") int columnCount,
        @JsonProperty("ticks") int tickCount,
        @JsonProperty("cells") List<CellConfiguration> cellConfigurations,
        @JsonProperty("commands") List<SimulationCommand> simulationCommands) {

    public LevelState {
        if (rowCount <= 0) {
            throw new IllegalArgumentException("Level row count must be positive; received row count [" + rowCount + "].");
        }
        if (columnCount <= 0) {
            throw new IllegalArgumentException("Level column count must be positive; received column count [" + columnCount + "].");
        }
        if (tickCount < 0) {
            throw new IllegalArgumentException("Level tick count cannot be negative; received tick count [" + tickCount + "].");
        }
        cellConfigurations = List.copyOf(cellConfigurations);
        simulationCommands = List.copyOf(simulationCommands);
    }
}