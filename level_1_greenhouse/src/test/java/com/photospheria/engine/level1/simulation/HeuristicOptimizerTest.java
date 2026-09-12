package com.photospheria.engine.level1.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photospheria.engine.level1.configuration.CellConfiguration;
import com.photospheria.engine.level1.configuration.LevelState;
import com.photospheria.engine.level1.simulation.HeuristicSimulationOptimizer.Coordinate;
import com.photospheria.engine.level1.simulation.HeuristicSimulationOptimizer.FrontierCoordinateSet;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HeuristicOptimizerTest {

    private static final String LEVEL_THREE_STATE_RESOURCE_NAME = "/level_three_state.json";
    private static final int ROCK_ENVIRONMENTAL_TERRAIN_TYPE = 4;

    @Test
    void heuristicOptimizerParsesOneHundredFiftyRowByOneHundredFiftyColumnGridWithEightHundredTickLimit() throws IOException {
        LevelState parsedLevelThreeState = parseLevelThreeStateFromResource();

        assertEquals(150, parsedLevelThreeState.rowCount());
        assertEquals(150, parsedLevelThreeState.columnCount());
        assertEquals(800, parsedLevelThreeState.tickCount());
        assertEquals(22500, parsedLevelThreeState.rowCount() * parsedLevelThreeState.columnCount());
        assertTrue(parsedLevelThreeState.animalsEnabled());

        SimulationGridState generatedSimulationGridState =
            SimulationOptimizer.createSimulationGridStateFromLevelState(parsedLevelThreeState);
        List<Coordinate> preComputedRockAdjacentCoordinates =
            HeuristicSimulationOptimizer.computePreComputedCoordinatesAdjacentToRockTerrain(generatedSimulationGridState);
        assertFalse(
            preComputedRockAdjacentCoordinates.isEmpty(),
            "The Level Three map must expose rock terrain clusters with cultivable adjacent coordinates.");
    }

    @Test
    void stoneReedPlacementStrictlyTargetsCoordinateAdjacentToRockTerrain() {
        assertEquals(11, HeuristicSimulationOptimizer.STONE_REED_PLANT_INDEX);

        SimulationGridState tinyRockGrid =
            SimulationOptimizer.createSimulationGridStateFromLevelState(createTinyLevelStateWithCentralRockCell());
        List<Coordinate> preComputedRockAdjacentCoordinates =
            HeuristicSimulationOptimizer.computePreComputedCoordinatesAdjacentToRockTerrain(tinyRockGrid);

        assertEquals(4, preComputedRockAdjacentCoordinates.size());
        for (Coordinate preComputedRockAdjacentCoordinate : preComputedRockAdjacentCoordinates) {
            assertTrue(
                isAdjacentToRockTerrainCell(tinyRockGrid, preComputedRockAdjacentCoordinate),
                "Every pre-computed rock adjacent coordinate must be a von Neumann neighbour of a rock terrain cell.");
            assertTrue(
                isCoordinateCultivableForPlantPlacement(tinyRockGrid, preComputedRockAdjacentCoordinate),
                "Every pre-computed rock adjacent coordinate must itself be cultivable and free of rock terrain.");
        }

        Random deterministicPlacementRandom = new Random(31L);
        List<Coordinate> emptyCultivableSoilCoordinates = List.of();
        for (int placementAttempt = 0; placementAttempt < 50; placementAttempt++) {
            Coordinate chosenStoneReedTarget = HeuristicSimulationOptimizer.selectTargetCoordinateForPlantPlacement(
                HeuristicSimulationOptimizer.STONE_REED_PLANT_INDEX,
                tinyRockGrid,
                deterministicPlacementRandom,
                preComputedRockAdjacentCoordinates,
                emptyCultivableSoilCoordinates);
            assertNotNull(
                chosenStoneReedTarget,
                "Stone Reed must always discover a rock adjacent target on a rock bordered grid.");
            assertTrue(
                isAdjacentToRockTerrainCell(tinyRockGrid, chosenStoneReedTarget),
                "Stone Reed placements must strictly land on a coordinate adjacent to rock terrain.");
        }
    }

    @Test
    void crimsonVinePlacementClustersOnlyNextToAlreadyOccupiedCells() {
        assertEquals(9, HeuristicSimulationOptimizer.CRIMSON_VINE_PLANT_INDEX);

        LevelState tinyEmptyLevelState = new LevelState(false, 4, 4, 0, List.of(), List.of());
        SimulationGridState clusteredSimulationGrid = SimulationOptimizer.createSimulationGridStateFromLevelState(tinyEmptyLevelState);
        clusteredSimulationGrid.registerNewPlantAt(1, 1, HeuristicSimulationOptimizer.CRIMSON_VINE_PLANT_INDEX);

        FrontierCoordinateSet freshlyBuiltFrontier = HeuristicSimulationOptimizer.buildFreshFrontierCoordinateSet(clusteredSimulationGrid);
        Random deterministicClusteringRandom = new Random(7L);
        List<Coordinate> emptyPreComputedRockCoordinates = List.of();
        List<Coordinate> emptyCultivableSoilCoordinates = List.of();

        for (int placementAttempt = 0; placementAttempt < 40; placementAttempt++) {
            Coordinate chosenCrimsonVineTarget = HeuristicSimulationOptimizer.selectTargetCoordinateForPlantPlacement(
                HeuristicSimulationOptimizer.CRIMSON_VINE_PLANT_INDEX,
                clusteredSimulationGrid,
                deterministicClusteringRandom,
                emptyPreComputedRockCoordinates,
                emptyCultivableSoilCoordinates,
                freshlyBuiltFrontier);
            assertNotNull(
                chosenCrimsonVineTarget,
                "Crimson Vine must always discover a coordinate adjacent to an already occupied cell.");
            assertTrue(
                isAdjacentToCurrentlyOccupiedCell(clusteredSimulationGrid, chosenCrimsonVineTarget),
                "Crimson Vine with the die_if_isolated weakness must only be placed adjacent to an occupied cell.");
        }
    }

    private LevelState parseLevelThreeStateFromResource() throws IOException {
        try (InputStream resourceInputStream = HeuristicOptimizerTest.class.getResourceAsStream(LEVEL_THREE_STATE_RESOURCE_NAME)) {
            Objects.requireNonNull(resourceInputStream, "The Level Three state resource must be present on the classpath.");
            return new ObjectMapper().readValue(resourceInputStream, LevelState.class);
        }
    }

    private LevelState createTinyLevelStateWithCentralRockCell() {
        return new LevelState(
            false,
            5,
            6,
            0,
            List.of(new CellConfiguration(2, 3, ROCK_ENVIRONMENTAL_TERRAIN_TYPE, 0)),
            List.of());
    }

    private boolean isAdjacentToRockTerrainCell(SimulationGridState simulationGridState, Coordinate targetCoordinate) {
        for (int verticalRowOffset = -1; verticalRowOffset <= 1; verticalRowOffset++) {
            for (int horizontalColumnOffset = -1; horizontalColumnOffset <= 1; horizontalColumnOffset++) {
                if (Math.abs(verticalRowOffset) + Math.abs(horizontalColumnOffset) != 1) {
                    continue;
                }
                int neighbourVerticalRowCoordinate = targetCoordinate.verticalRowCoordinate() + verticalRowOffset;
                int neighbourHorizontalColumnCoordinate = targetCoordinate.horizontalColumnCoordinate() + horizontalColumnOffset;
                if (isCoordinateWithinGridBounds(simulationGridState, neighbourVerticalRowCoordinate, neighbourHorizontalColumnCoordinate)
                    && simulationGridState.environmentalTerrainTypeAt(neighbourVerticalRowCoordinate, neighbourHorizontalColumnCoordinate)
                    == ROCK_ENVIRONMENTAL_TERRAIN_TYPE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCoordinateCultivableForPlantPlacement(SimulationGridState simulationGridState, Coordinate targetCoordinate) {
        return simulationGridState.environmentalTerrainTypeAt(
            targetCoordinate.verticalRowCoordinate(),
            targetCoordinate.horizontalColumnCoordinate()) != ROCK_ENVIRONMENTAL_TERRAIN_TYPE;
    }

    private boolean isAdjacentToCurrentlyOccupiedCell(SimulationGridState simulationGridState, Coordinate targetCoordinate) {
        for (int verticalRowOffset = -1; verticalRowOffset <= 1; verticalRowOffset++) {
            for (int horizontalColumnOffset = -1; horizontalColumnOffset <= 1; horizontalColumnOffset++) {
                if (Math.abs(verticalRowOffset) + Math.abs(horizontalColumnOffset) != 1) {
                    continue;
                }
                int neighbourVerticalRowCoordinate = targetCoordinate.verticalRowCoordinate() + verticalRowOffset;
                int neighbourHorizontalColumnCoordinate = targetCoordinate.horizontalColumnCoordinate() + horizontalColumnOffset;
                if (isCoordinateWithinGridBounds(simulationGridState, neighbourVerticalRowCoordinate, neighbourHorizontalColumnCoordinate)
                    && simulationGridState.plantIndexOfCell(neighbourVerticalRowCoordinate, neighbourHorizontalColumnCoordinate)
                    != SimulationGridState.DEAD_PLANT_INDEX) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCoordinateWithinGridBounds(
            SimulationGridState simulationGridState,
            int verticalRowCoordinate,
            int horizontalColumnCoordinate) {

        return verticalRowCoordinate >= 0
            && verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount()
            && horizontalColumnCoordinate >= 0
            && horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount();
    }
}