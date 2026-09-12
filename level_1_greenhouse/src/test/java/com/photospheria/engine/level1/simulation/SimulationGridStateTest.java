package com.photospheria.engine.level1.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photospheria.engine.level1.configuration.LevelState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimulationGridStateTest {

    private static final String LEVEL_ONE_STATE_RESOURCE_NAME = "/level_one_state.json";

    private ObjectMapper objectMapper;

    @BeforeEach
    void initialiseJacksonObjectMapper() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void gridDimensionsMatchFiftyRowsByFiftyColumnsFromLevelState() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationGridState simulationGridState = new SimulationGridState(parsedLevelState);
        assertEquals(50, simulationGridState.verticalRowCoordinateCount());
        assertEquals(50, simulationGridState.horizontalColumnCoordinateCount());
    }

    @Test
    void everyCellInNutrientGridInitializesToOneHundredNutrientPoints() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationGridState simulationGridState = new SimulationGridState(parsedLevelState);

        byte[][] cellularNutrientCapacityGrid = simulationGridState.cellularNutrientCapacityGrid();
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount(); verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount(); horizontalColumnCoordinate++) {
                assertEquals(
                    100,
                    cellularNutrientPointsAt(verticalRowCoordinate, horizontalColumnCoordinate, simulationGridState),
                    "Nutrient points at row coordinate ["
                        + verticalRowCoordinate
                        + "] and column coordinate ["
                        + horizontalColumnCoordinate
                        + "] must be exactly 100.");
            }
        }
    }

    @Test
    void plantPopulationGridInitializesToZeroForEveryCell() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationGridState simulationGridState = new SimulationGridState(parsedLevelState);

        short[][] plantPopulationGrid = simulationGridState.plantPopulationGrid();
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount(); verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount(); horizontalColumnCoordinate++) {
                assertEquals(
                    0,
                    plantPopulationGrid[verticalRowCoordinate][horizontalColumnCoordinate],
                    "Plant population at row coordinate ["
                        + verticalRowCoordinate
                        + "] and column coordinate ["
                        + horizontalColumnCoordinate
                        + "] must initialise to zero.");
            }
        }
    }

    @Test
    void soilAndTerrainGridsMapCellAtRowZeroColumnTen() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationGridState simulationGridState = new SimulationGridState(parsedLevelState);

        assertEquals(2, simulationGridState.environmentalTerrainTypeAt(0, 10));
        assertEquals(0, simulationGridState.environmentalSoilTypeAt(0, 10));
    }

    @Test
    void soilAndTerrainGridsMapCellAtRowThirtyColumnEleven() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationGridState simulationGridState = new SimulationGridState(parsedLevelState);

        assertEquals(0, simulationGridState.environmentalTerrainTypeAt(30, 11));
        assertEquals(2, simulationGridState.environmentalSoilTypeAt(30, 11));
    }

    @Test
    void soilAndTerrainGridsMapCellAtRowZeroColumnThirtyOne() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationGridState simulationGridState = new SimulationGridState(parsedLevelState);

        assertEquals(0, simulationGridState.environmentalTerrainTypeAt(0, 31));
        assertEquals(2, simulationGridState.environmentalSoilTypeAt(0, 31));
    }

    @Test
    void soilAndTerrainGridsMapUnpopulatedCoordinatesToDefaultZeroTerrainAndSoil() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationGridState simulationGridState = new SimulationGridState(parsedLevelState);

        assertEquals(0, simulationGridState.environmentalTerrainTypeAt(20, 5));
        assertEquals(0, simulationGridState.environmentalSoilTypeAt(20, 5));
    }

    @Test
    void fourPrimitiveArraysAreAllocatedWithFullGridDimensions() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationGridState simulationGridState = new SimulationGridState(parsedLevelState);

        assertNotNull(simulationGridState.plantPopulationGrid());
        assertNotNull(simulationGridState.cellularNutrientCapacityGrid());
        assertNotNull(simulationGridState.environmentalSoilTypeGrid());
        assertNotNull(simulationGridState.environmentalTerrainTypeGrid());

        assertEquals(50, simulationGridState.plantPopulationGrid().length);
        assertEquals(50, simulationGridState.cellularNutrientCapacityGrid().length);
        assertEquals(50, simulationGridState.environmentalSoilTypeGrid().length);
        assertEquals(50, simulationGridState.environmentalTerrainTypeGrid().length);
    }

    private int cellularNutrientPointsAt(int verticalRowCoordinate, int horizontalColumnCoordinate, SimulationGridState simulationGridState) {
        return Byte.toUnsignedInt(simulationGridState.cellularNutrientCapacityGrid()[verticalRowCoordinate][horizontalColumnCoordinate]);
    }

    private LevelState parseLevelStateFromResource() throws IOException {
        String levelOneStateJson = loadLevelOneStateAsString();
        return objectMapper.readValue(levelOneStateJson, LevelState.class);
    }

    private String loadLevelOneStateAsString() throws IOException {
        try (InputStream resourceInputStream = SimulationGridStateTest.class.getResourceAsStream(LEVEL_ONE_STATE_RESOURCE_NAME)) {
            assertNotNull(resourceInputStream, "Level state resource must be present on the classpath.");
            return new String(resourceInputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}