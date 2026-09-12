package com.photospheria.engine.level1.simulation;

import com.photospheria.engine.level1.configuration.CellConfiguration;
import com.photospheria.engine.level1.configuration.LevelState;
import java.util.Arrays;

public class SimulationGridState {

    private static final byte INITIAL_CELLULAR_NUTRIENT_POINTS_PER_CELL = 100;

    private final int verticalRowCoordinateCount;
    private final int horizontalColumnCoordinateCount;

    private final short[][] plantPopulationGrid;
    private final byte[][] cellularNutrientCapacityGrid;
    private final byte[][] environmentalSoilTypeGrid;
    private final byte[][] environmentalTerrainTypeGrid;

    public SimulationGridState(LevelState levelState) {
        if (levelState == null) {
            throw new IllegalArgumentException("LevelState cannot be null.");
        }

        this.verticalRowCoordinateCount = levelState.rowCount();
        this.horizontalColumnCoordinateCount = levelState.columnCount();
        this.plantPopulationGrid = new short[verticalRowCoordinateCount][horizontalColumnCoordinateCount];
        this.cellularNutrientCapacityGrid = new byte[verticalRowCoordinateCount][horizontalColumnCoordinateCount];
        this.environmentalSoilTypeGrid = new byte[verticalRowCoordinateCount][horizontalColumnCoordinateCount];
        this.environmentalTerrainTypeGrid = new byte[verticalRowCoordinateCount][horizontalColumnCoordinateCount];

        initialiseEveryCellToFullCellularNutrientCapacity();
        populateEnvironmentalSoilAndTerrainGridsFromCellConfigurations(levelState);
    }

    private void initialiseEveryCellToFullCellularNutrientCapacity() {
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < verticalRowCoordinateCount; verticalRowCoordinate++) {
            Arrays.fill(
                cellularNutrientCapacityGrid[verticalRowCoordinate],
                INITIAL_CELLULAR_NUTRIENT_POINTS_PER_CELL);
        }
    }

    private void populateEnvironmentalSoilAndTerrainGridsFromCellConfigurations(LevelState levelState) {
        for (CellConfiguration cellConfiguration : levelState.cellConfigurations()) {
            int verticalRowCoordinate = cellConfiguration.row();
            int horizontalColumnCoordinate = cellConfiguration.column();
            validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);

            environmentalSoilTypeGrid[verticalRowCoordinate][horizontalColumnCoordinate] = (byte) cellConfiguration.soil();
            environmentalTerrainTypeGrid[verticalRowCoordinate][horizontalColumnCoordinate] = (byte) cellConfiguration.terrain();
        }
    }

    private void validateCoordinateWithinGridBounds(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        if (verticalRowCoordinate < 0 || verticalRowCoordinate >= verticalRowCoordinateCount) {
            throw new IllegalArgumentException(
                "Vertical row coordinate [" + verticalRowCoordinate + "] is outside the grid bounds of row count ["
                    + verticalRowCoordinateCount + "].");
        }
        if (horizontalColumnCoordinate < 0 || horizontalColumnCoordinate >= horizontalColumnCoordinateCount) {
            throw new IllegalArgumentException(
                "Horizontal column coordinate [" + horizontalColumnCoordinate + "] is outside the grid bounds of column count ["
                    + horizontalColumnCoordinateCount + "].");
        }
    }

    public int verticalRowCoordinateCount() {
        return verticalRowCoordinateCount;
    }

    public int horizontalColumnCoordinateCount() {
        return horizontalColumnCoordinateCount;
    }

    public short[][] plantPopulationGrid() {
        return plantPopulationGrid;
    }

    public byte[][] cellularNutrientCapacityGrid() {
        return cellularNutrientCapacityGrid;
    }

    public byte[][] environmentalSoilTypeGrid() {
        return environmentalSoilTypeGrid;
    }

    public byte[][] environmentalTerrainTypeGrid() {
        return environmentalTerrainTypeGrid;
    }

    public int cellularNutrientPointsAt(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        return Byte.toUnsignedInt(cellularNutrientCapacityGrid[verticalRowCoordinate][horizontalColumnCoordinate]);
    }

    public int environmentalSoilTypeAt(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        return Byte.toUnsignedInt(environmentalSoilTypeGrid[verticalRowCoordinate][horizontalColumnCoordinate]);
    }

    public int environmentalTerrainTypeAt(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        return Byte.toUnsignedInt(environmentalTerrainTypeGrid[verticalRowCoordinate][horizontalColumnCoordinate]);
    }
}