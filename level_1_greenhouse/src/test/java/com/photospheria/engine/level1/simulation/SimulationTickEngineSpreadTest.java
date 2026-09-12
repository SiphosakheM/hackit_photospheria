package com.photospheria.engine.level1.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimulationTickEngineSpreadTest {

    private static final int GRID_VERTICAL_ROW_COUNT = 50;
    private static final int GRID_HORIZONTAL_COLUMN_COUNT = 50;

    private SimulationTickEngine simulationTickEngine;

    @BeforeEach
    void initialiseSimulationTickEngine() {
        simulationTickEngine = new SimulationTickEngine();
    }

    @Test
    void vonNeumannSpreadReturnsFourOrthogonallyAdjacentCellsCenteredAtRowTwentyFiveColumnTwentyFive() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            25, 25, 1, "VonNeumann", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(24, 25),
                coordinateAt(25, 24),
                coordinateAt(25, 26),
                coordinateAt(26, 25)),
            resolvedTargetCoordinates);
    }

    @Test
    void mooreSpreadReturnsEightSurroundingCellsCenteredAtRowTwentyFiveColumnTwentyFive() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            25, 25, 1, "Moore", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(24, 24),
                coordinateAt(24, 25),
                coordinateAt(24, 26),
                coordinateAt(25, 24),
                coordinateAt(25, 26),
                coordinateAt(26, 24),
                coordinateAt(26, 25),
                coordinateAt(26, 26)),
            resolvedTargetCoordinates);
    }

    @Test
    void rowSpreadReturnsTwoHorizontalAdjacentCellsCenteredAtRowTwentyFiveColumnTwentyFive() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            25, 25, 1, "Row", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(25, 24),
                coordinateAt(25, 26)),
            resolvedTargetCoordinates);
    }

    @Test
    void columnSpreadReturnsTwoVerticalAdjacentCellsCenteredAtRowTwentyFiveColumnTwentyFive() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            25, 25, 1, "Column", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(24, 25),
                coordinateAt(26, 25)),
            resolvedTargetCoordinates);
    }

    @Test
    void crossHatchSpreadReturnsFourDiagonalAdjacentCellsCenteredAtRowTwentyFiveColumnTwentyFive() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            25, 25, 1, "CrossHatch", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(24, 24),
                coordinateAt(24, 26),
                coordinateAt(26, 24),
                coordinateAt(26, 26)),
            resolvedTargetCoordinates);
    }

    @Test
    void spreadFromTopLeftOriginNeverProducesNegativeOrOutOfBoundsCoordinatesForAnyGeometry() {
        assertAllResolvedCoordinatesStayWithinGridBoundsForGeometryAtOrigin("VonNeumann");
        assertAllResolvedCoordinatesStayWithinGridBoundsForGeometryAtOrigin("Moore");
        assertAllResolvedCoordinatesStayWithinGridBoundsForGeometryAtOrigin("Row");
        assertAllResolvedCoordinatesStayWithinGridBoundsForGeometryAtOrigin("Column");
        assertAllResolvedCoordinatesStayWithinGridBoundsForGeometryAtOrigin("CrossHatch");
    }

    @Test
    void vonNeumannSpreadAtTopLeftOriginReturnsOnlyInBoundsNeighbouringCells() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            0, 0, 1, "VonNeumann", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(0, 1),
                coordinateAt(1, 0)),
            resolvedTargetCoordinates);
    }

    @Test
    void mooreSpreadAtTopLeftOriginReturnsOnlyInBoundsNeighbouringCells() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            0, 0, 1, "Moore", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(0, 1),
                coordinateAt(1, 0),
                coordinateAt(1, 1)),
            resolvedTargetCoordinates);
    }

    @Test
    void rowSpreadAtTopLeftOriginReturnsOnlyTheCellToTheRight() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            0, 0, 1, "Row", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(coordinateAt(0, 1)),
            resolvedTargetCoordinates);
    }

    @Test
    void columnSpreadAtTopLeftOriginReturnsOnlyTheCellBelow() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            0, 0, 1, "Column", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(coordinateAt(1, 0)),
            resolvedTargetCoordinates);
    }

    @Test
    void crossHatchSpreadAtTopLeftOriginReturnsOnlyTheDiagonalCellBelowRight() {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            0, 0, 1, "CrossHatch", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(coordinateAt(1, 1)),
            resolvedTargetCoordinates);
    }

    @Test
    void unknownSpatialPropagationGeometryTypeThrowsIllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> simulationTickEngine.resolveSpreadTargetCoordinates(
                25, 25, 1, "Hexagonal", GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT));
    }

    private void assertAllResolvedCoordinatesStayWithinGridBoundsForGeometryAtOrigin(String spatialPropagationGeometryType) {
        List<SimulationTickEngine.SimulationCoordinate> resolvedTargetCoordinates = simulationTickEngine.resolveSpreadTargetCoordinates(
            0, 0, 1, spatialPropagationGeometryType, GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertTrue(
            resolvedTargetCoordinates.stream().allMatch(
                simulationCoordinate ->
                    simulationCoordinate.targetVerticalRowCoordinate() >= 0
                        && simulationCoordinate.targetVerticalRowCoordinate() < GRID_VERTICAL_ROW_COUNT
                        && simulationCoordinate.targetHorizontalColumnCoordinate() >= 0
                        && simulationCoordinate.targetHorizontalColumnCoordinate() < GRID_HORIZONTAL_COLUMN_COUNT),
            "All resolved target coordinates for geometry type ["
                + spatialPropagationGeometryType
                + "] must stay within the grid boundaries.");
    }

    private SimulationTickEngine.SimulationCoordinate coordinateAt(int targetVerticalRowCoordinate, int targetHorizontalColumnCoordinate) {
        return new SimulationTickEngine.SimulationCoordinate(targetVerticalRowCoordinate, targetHorizontalColumnCoordinate);
    }
}