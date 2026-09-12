package com.photospheria.engine.level1.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CellConfiguration(
        @JsonProperty("row") int row,
        @JsonProperty("col") int column,
        @JsonProperty("terrain") int terrain,
        @JsonProperty("soil") int soil) {

    public CellConfiguration {
        if (row < 0) {
            throw new IllegalArgumentException("Cell row index cannot be negative; received row index [" + row + "].");
        }
        if (column < 0) {
            throw new IllegalArgumentException("Cell column index cannot be negative; received column index [" + column + "].");
        }
        if (terrain < 0) {
            throw new IllegalArgumentException("Cell terrain cannot be negative; received terrain [" + terrain + "].");
        }
        if (soil < 0) {
            throw new IllegalArgumentException("Cell soil cannot be negative; received soil [" + soil + "].");
        }
    }
}