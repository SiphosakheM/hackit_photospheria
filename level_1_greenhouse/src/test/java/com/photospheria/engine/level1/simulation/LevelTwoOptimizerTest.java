package com.photospheria.engine.level1.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photospheria.engine.level1.configuration.LevelState;
import com.photospheria.engine.level2.ecosystem.EcosystemValidator;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LevelTwoOptimizerTest {

    private static final String LEVEL_TWO_STATE_RESOURCE_NAME = "/level_two_state.json";

    @Test
    void optimizerInitialisesSeventyRowsByOneHundredColumnsGridStateFromParsedLevelTwoState() throws IOException {
        LevelState parsedLevelTwoState = parseLevelTwoStateFromResource();

        SimulationGridState generatedSimulationGridState = SimulationOptimizer.createSimulationGridStateFromLevelState(parsedLevelTwoState);

        assertEquals(70, generatedSimulationGridState.verticalRowCoordinateCount());
        assertEquals(100, generatedSimulationGridState.horizontalColumnCoordinateCount());
        assertTrue(parsedLevelTwoState.animalsEnabled());
        assertEquals(7000, generatedSimulationGridState.verticalRowCoordinateCount() * generatedSimulationGridState.horizontalColumnCoordinateCount());
    }

    @Test
    void optimizerRegistersRainEventOnEcosystemValidatorAtTickTwoHundredFifty() throws IOException {
        LevelState parsedLevelTwoState = parseLevelTwoStateFromResource();
        EcosystemValidator ecosystemValidator = SimulationOptimizer.loadEcosystemValidatorForLevelTwo();

        SimulationOptimizer.applyScheduledCommandsForCurrentTick(parsedLevelTwoState.simulationCommands(), 249, ecosystemValidator);
        assertFalse(
            ecosystemValidator.currentlyActiveWeatherEvents().contains("Rain"),
            "The Rain weather event must not be registered before tick 250.");

        SimulationOptimizer.applyScheduledCommandsForCurrentTick(parsedLevelTwoState.simulationCommands(), 250, ecosystemValidator);
        assertTrue(
            ecosystemValidator.currentlyActiveWeatherEvents().contains("Rain"),
            "The Rain weather event command at tick 250 must register on the ecosystem validator.");
    }

    @Test
    void simulatingAboveFivePercentBlueMossCoverageWithRainUnlocksMireBloomInOptimizerActivePlacementPool() throws IOException {
        LevelState parsedLevelTwoState = parseLevelTwoStateFromResource();
        SimulationGridState simulationGridState = SimulationOptimizer.createSimulationGridStateFromLevelState(parsedLevelTwoState);
        EcosystemValidator ecosystemValidator = SimulationOptimizer.loadEcosystemValidatorForLevelTwo();

        int blueMossPlantIndex = requirePlantIndexMappedForOfficialSpeciesName("Blue Moss");
        int mireBloomPlantIndex = requirePlantIndexMappedForOfficialSpeciesName("Mire Bloom");
        placePlantSpeciesAcrossRowMajorCells(simulationGridState, blueMossPlantIndex, 0, 400);

        ecosystemValidator.calculateCurrentEcosystemStatistics(simulationGridState);
        ecosystemValidator.evaluateActiveAnimals();

        Set<Integer> currentlyUnlockedPlantIndexesPool = new LinkedHashSet<>(SimulationOptimizer.LEVEL_TWO_STARTER_PLANT_INDEXES);
        Set<Integer> probabilityWeightedNewlyUnlockedSpeciesIndexes = new LinkedHashSet<>();

        SimulationOptimizer.refreshCurrentlyUnlockedPlantIndexesPool(
            ecosystemValidator,
            currentlyUnlockedPlantIndexesPool,
            probabilityWeightedNewlyUnlockedSpeciesIndexes);
        assertFalse(
            currentlyUnlockedPlantIndexesPool.contains(mireBloomPlantIndex),
            "Mire Bloom must remain locked while no Rain event has yet been registered.");

        SimulationOptimizer.applyScheduledCommandsForCurrentTick(parsedLevelTwoState.simulationCommands(), 250, ecosystemValidator);
        SimulationOptimizer.refreshCurrentlyUnlockedPlantIndexesPool(
            ecosystemValidator,
            currentlyUnlockedPlantIndexesPool,
            probabilityWeightedNewlyUnlockedSpeciesIndexes);

        assertTrue(
            ecosystemValidator.currentlyActiveWeatherEvents().contains("Rain"),
            "The Rain weather event must be active on the validator after tick 250.");
        assertTrue(
            currentlyUnlockedPlantIndexesPool.contains(mireBloomPlantIndex),
            "Mire Bloom must enter the optimizer's active placement pool once Blue Moss coverage exceeds 5% under Rain.");
        assertTrue(
            probabilityWeightedNewlyUnlockedSpeciesIndexes.contains(mireBloomPlantIndex),
            "Mire Bloom must be tracked as a newly unlocked species for weighted placement selection.");
    }

    @Test
    void levelTwoSimulationRecordsPlacementActionsWithTickRowColumnAndPlantIndexForSubmission() throws IOException {
        LevelState tinyLevelTwoState = new LevelState(true, 3, 5, 4, List.of(), List.of());
        EcosystemValidator ecosystemValidator = SimulationOptimizer.loadEcosystemValidatorForLevelTwo();

        SimulationOptimizer.SimulationRunScore simulationRunScore =
            SimulationOptimizer.simulateDeterministicRunWithDynamicPlacementPools(tinyLevelTwoState, 1L, ecosystemValidator);
        List<SimulationOptimizer.PlantingAction> placementActionsRecord = simulationRunScore.placementActionsRecord();

        assertFalse(placementActionsRecord.isEmpty(), "Every successful placement must be recorded as a PlantingAction.");

        for (SimulationOptimizer.PlantingAction plantingAction : placementActionsRecord) {
            assertTrue(
                plantingAction.executionTick() >= 1 && plantingAction.executionTick() <= tinyLevelTwoState.tickCount(),
                "Level Two placement actions must carry an execution tick inside the simulated tick range.");
            assertTrue(
                plantingAction.verticalRowCoordinate() >= 0 && plantingAction.verticalRowCoordinate() < 3,
                "Level Two placement vertical row coordinates must stay inside the three row grid.");
            assertTrue(
                plantingAction.horizontalColumnCoordinate() >= 0 && plantingAction.horizontalColumnCoordinate() < 5,
                "Level Two placement horizontal column coordinates must stay inside the five column grid.");
            assertTrue(
                plantingAction.plantIndex() > 0,
                "Level Two placement actions must always reference a valid positive plant index.");
        }

        assertTrue(
            placementActionsRecord.size() <= 3 * 5,
            "Placement actions cannot exceed the finite fifteen cell capacity of the tiny grid.");
    }

    private LevelState parseLevelTwoStateFromResource() throws IOException {
        try (InputStream resourceInputStream = LevelTwoOptimizerTest.class.getResourceAsStream(LEVEL_TWO_STATE_RESOURCE_NAME)) {
            Objects.requireNonNull(resourceInputStream, "The Level Two state resource must be present on the classpath.");
            return new ObjectMapper().readValue(resourceInputStream, LevelState.class);
        }
    }

    private int requirePlantIndexMappedForOfficialSpeciesName(String officialPlantSpeciesName) {
        Integer mappedPlantIndex = EcosystemValidator.DEFAULT_LEVEL_TWO_PLANT_SPECIES_NAME_TO_INDEX_MAPPING.get(officialPlantSpeciesName);
        if (mappedPlantIndex == null) {
            throw new AssertionError("Plant species name [" + officialPlantSpeciesName + "] is missing from the default Level Two mapping.");
        }
        return mappedPlantIndex;
    }

    private void placePlantSpeciesAcrossRowMajorCells(
            SimulationGridState simulationGridState,
            int plantIndexToPlace,
            int startingCellOrdinal,
            int cellCountToPlace) {

        int horizontalColumnCoordinateCount = simulationGridState.horizontalColumnCoordinateCount();
        for (int cellPlacementOffset = 0; cellPlacementOffset < cellCountToPlace; cellPlacementOffset++) {
            int targetCellOrdinal = startingCellOrdinal + cellPlacementOffset;
            int targetVerticalRowCoordinate = targetCellOrdinal / horizontalColumnCoordinateCount;
            int targetHorizontalColumnCoordinate = targetCellOrdinal % horizontalColumnCoordinateCount;
            simulationGridState.registerNewPlantAt(targetVerticalRowCoordinate, targetHorizontalColumnCoordinate, plantIndexToPlace);
        }
    }
}