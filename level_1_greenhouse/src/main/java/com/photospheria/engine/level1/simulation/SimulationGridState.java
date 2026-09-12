package com.photospheria.engine.level1.simulation;

import com.photospheria.engine.level1.configuration.CellConfiguration;
import com.photospheria.engine.level1.configuration.LevelState;
import java.util.Arrays;

public class SimulationGridState {

    public static final int MAXIMUM_CELLULAR_NUTRIENT_POINTS_PER_CELL = 100;
    public static final int DEAD_PLANT_INDEX = 0;

    private final int verticalRowCoordinateCount;
    private final int horizontalColumnCoordinateCount;

    private final short[][] plantPopulationGrid;
    private final short[][] plantAgeInTicksGrid;
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
        this.plantAgeInTicksGrid = new short[verticalRowCoordinateCount][horizontalColumnCoordinateCount];
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
                (byte) MAXIMUM_CELLULAR_NUTRIENT_POINTS_PER_CELL);
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

    public short[][] plantAgeInTicksGrid() {
        return plantAgeInTicksGrid;
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

    public int plantIndexOfCell(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        return Short.toUnsignedInt(plantPopulationGrid[verticalRowCoordinate][horizontalColumnCoordinate]);
    }

    public int plantAgeInTicksAt(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        return Short.toUnsignedInt(plantAgeInTicksGrid[verticalRowCoordinate][horizontalColumnCoordinate]);
    }

    public void registerNewPlantAt(int verticalRowCoordinate, int horizontalColumnCoordinate, int plantIndex) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        if (plantIndex < 0) {
            throw new IllegalArgumentException("Plant index cannot be negative; received [" + plantIndex + "].");
        }
        if (plantIndex > Short.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Plant index [" + plantIndex + "] exceeds the maximum storable plant index [" + Short.MAX_VALUE + "].");
        }
        plantPopulationGrid[verticalRowCoordinate][horizontalColumnCoordinate] = (short) plantIndex;
        plantAgeInTicksGrid[verticalRowCoordinate][horizontalColumnCoordinate] = 0;
    }

    public void terminatePlantAt(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        plantPopulationGrid[verticalRowCoordinate][horizontalColumnCoordinate] = DEAD_PLANT_INDEX;
        plantAgeInTicksGrid[verticalRowCoordinate][horizontalColumnCoordinate] = 0;
    }

    public void incrementPlantAgeInTicksAt(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        int currentAgeInTicks = Short.toUnsignedInt(plantAgeInTicksGrid[verticalRowCoordinate][horizontalColumnCoordinate]);
        if (currentAgeInTicks < Short.MAX_VALUE) {
            plantAgeInTicksGrid[verticalRowCoordinate][horizontalColumnCoordinate] = (short) (currentAgeInTicks + 1);
        }
    }

    public void setCellularNutrientPointsAt(int verticalRowCoordinate, int horizontalColumnCoordinate, int nutrientPoints) {
        validateCoordinateWithinGridBounds(verticalRowCoordinate, horizontalColumnCoordinate);
        if (nutrientPoints < 0 || nutrientPoints > MAXIMUM_CELLULAR_NUTRIENT_POINTS_PER_CELL) {
            throw new IllegalArgumentException(
                "Nutrient points [" + nutrientPoints + "] must be within the range [0, "
                    + MAXIMUM_CELLULAR_NUTRIENT_POINTS_PER_CELL + "].");
        }
        cellularNutrientCapacityGrid[verticalRowCoordinate][horizontalColumnCoordinate] = (byte) nutrientPoints;
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