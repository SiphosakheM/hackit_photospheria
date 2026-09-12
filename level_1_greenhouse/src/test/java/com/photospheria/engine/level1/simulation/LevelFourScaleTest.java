package com.photospheria.engine.level1.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photospheria.engine.level1.configuration.LevelState;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class LevelFourScaleTest {

    private static final String LEVEL_FOUR_STATE_RESOURCE_NAME = "/level_four_state.json";

    @Test
    void simulationGridStateInstantiatesTwoHundredRowsByThreeHundredColumnsFromMassiveLevelFourCellsArray() throws IOException {
        LevelState parsedLevelFourState = parseLevelFourStateFromResource();

        assertEquals(
            SimulationOptimizer.DEFAULT_LEVEL_FOUR_VERTICAL_ROW_COORDINATE_COUNT,
            parsedLevelFourState.rowCount(),
            "The parsed Level Four state must declare the global row dimension of two hundred.");
        assertEquals(
            SimulationOptimizer.DEFAULT_LEVEL_FOUR_HORIZONTAL_COLUMN_COORDINATE_COUNT,
            parsedLevelFourState.columnCount(),
            "The parsed Level Four state must declare the global column dimension of three hundred.");
        assertEquals(800, parsedLevelFourState.tickCount());
        assertEquals(6413, parsedLevelFourState.cellConfigurations().size());
        assertTrue(parsedLevelFourState.animalsEnabled());

        SimulationGridState generatedSimulationGridState =
            SimulationOptimizer.createSimulationGridStateFromLevelState(parsedLevelFourState);

        assertEquals(200, generatedSimulationGridState.verticalRowCoordinateCount());
        assertEquals(300, generatedSimulationGridState.horizontalColumnCoordinateCount());
        assertEquals(
            60000,
            generatedSimulationGridState.verticalRowCoordinateCount()
                * generatedSimulationGridState.horizontalColumnCoordinateCount(),
            "A two hundred by three hundred Level Four grid must expose exactly sixty thousand cells.");
    }

    @Test
    void primitiveGridArraysRemainMemoryLeanWithoutPerCellObjectAllocations() throws IOException {
        LevelState parsedLevelFourState = parseLevelFourStateFromResource();
        SimulationGridState generatedSimulationGridState =
            SimulationOptimizer.createSimulationGridStateFromLevelState(parsedLevelFourState);

        assertEquals(
            short[][].class.getName(),
            generatedSimulationGridState.plantPopulationGrid().getClass().getName(),
            "The plant population grid must remain a primitive short two dimensional array.");
        assertEquals(
            short[][].class.getName(),
            generatedSimulationGridState.plantAgeInTicksGrid().getClass().getName(),
            "The plant age grid must remain a primitive short two dimensional array.");
        assertEquals(
            byte[][].class.getName(),
            generatedSimulationGridState.cellularNutrientCapacityGrid().getClass().getName(),
            "The cellular nutrient grid must remain a primitive byte two dimensional array.");
        assertEquals(
            byte[][].class.getName(),
            generatedSimulationGridState.environmentalSoilTypeGrid().getClass().getName(),
            "The environmental soil type grid must remain a primitive byte two dimensional array.");
        assertEquals(
            byte[][].class.getName(),
            generatedSimulationGridState.environmentalTerrainTypeGrid().getClass().getName(),
            "The environmental terrain type grid must remain a primitive byte two dimensional array.");

        for (int verticalRowCoordinate = 0; verticalRowCoordinate < 200; verticalRowCoordinate++) {
            assertEquals(
                300,
                generatedSimulationGridState.plantPopulationGrid()[verticalRowCoordinate].length,
                "Every two dimensional array row must expose exactly three hundred primitive elements.");
        }
    }

    @Test
    void fastFailEntropyDecisionAbortsSeedsWhoseEntropyScoreFallsBelowBaselineThreshold() {
        LevelState emptyLevelFourState = new LevelState(false, 200, 300, 100, List.of(), List.of());
        SimulationGridState emptySimulationGrid = SimulationOptimizer.createSimulationGridStateFromLevelState(emptyLevelFourState);

        long emptyGridEntropyScore = HeuristicSimulationOptimizer.calculateLivingPlantEntropyScore(emptySimulationGrid);
        assertEquals(0L, emptyGridEntropyScore);
        assertTrue(
            HeuristicSimulationOptimizer.shouldFastFailSimulationForBelowBaselineEntropy(emptyGridEntropyScore),
            "A grid with zero living plants must fall below the fast-fail entropy baseline.");

        emptySimulationGrid.registerNewPlantAt(0, 0, HeuristicSimulationOptimizer.STONE_REED_PLANT_INDEX);
        emptySimulationGrid.registerNewPlantAt(0, 1, HeuristicSimulationOptimizer.CRIMSON_VINE_PLANT_INDEX);
        emptySimulationGrid.registerNewPlantAt(0, 2, HeuristicSimulationOptimizer.GRASS_PLANT_INDEX);

        long occupiedGridEntropyScore = HeuristicSimulationOptimizer.calculateLivingPlantEntropyScore(emptySimulationGrid);
        long expectedEntropyScore = 3L
            + 3L * HeuristicSimulationOptimizer.FAST_FAIL_LIVING_PLANT_DIVERSITY_WEIGHT;
        assertEquals(
            expectedEntropyScore,
            occupiedGridEntropyScore,
            "Entropy must combine the living plant count with weighted living species diversity.");

        assertFalse(
            HeuristicSimulationOptimizer.shouldFastFailSimulationForBelowBaselineEntropy(10_000L),
            "A high-potential entropy score must never trigger the fast-fail abort.");
    }

    private LevelState parseLevelFourStateFromResource() throws IOException {
        try (InputStream resourceInputStream = LevelFourScaleTest.class.getResourceAsStream(LEVEL_FOUR_STATE_RESOURCE_NAME)) {
            Objects.requireNonNull(resourceInputStream, "The Level Four state resource must be present on the classpath.");
            return new ObjectMapper().readValue(resourceInputStream, LevelState.class);
        }
    }
}