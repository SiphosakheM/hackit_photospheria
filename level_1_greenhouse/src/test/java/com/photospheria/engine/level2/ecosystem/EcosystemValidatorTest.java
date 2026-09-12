package com.photospheria.engine.level2.ecosystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photospheria.engine.level1.configuration.LevelState;
import com.photospheria.engine.level1.simulation.SimulationGridState;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EcosystemValidatorTest {

    private static final int GRASS_PLANT_INDEX = 1;

    @Test
    void coverageCalculationForThreeHundredAndFiftyGrassCellsOnSeventyByOneHundredGridIsExactlyFivePercent() throws IOException {
        EcosystemValidator ecosystemValidator = buildEcosystemValidatorWithOfficialLevelTwoRules();

        SimulationGridState seventyByOneHundredGridState = createSeventyByOneHundredGridState();
        placePlantSpeciesAcrossRowMajorCells(seventyByOneHundredGridState, GRASS_PLANT_INDEX, 0, 350);

        ecosystemValidator.calculateCurrentEcosystemStatistics(seventyByOneHundredGridState);

        double grassCoverageFraction = ecosystemValidator.currentEcosystemPlantSpeciesCoverageFraction(GRASS_PLANT_INDEX);
        assertEquals(0.05, grassCoverageFraction, 0.000001);
        assertEquals(350, ecosystemValidator.currentEcosystemPlantSpeciesCellCount(GRASS_PLANT_INDEX));
    }

    @Test
    void verdelopesBecomeActiveAnimalsOnlyWhenGrassCoverageReachesAndExceedsFivePercentThreshold() throws IOException {
        EcosystemValidator ecosystemValidator = buildEcosystemValidatorWithOfficialLevelTwoRules();

        SimulationGridState seventyByOneHundredGridState = createSeventyByOneHundredGridState();
        placePlantSpeciesAcrossRowMajorCells(seventyByOneHundredGridState, GRASS_PLANT_INDEX, 0, 100);

        ecosystemValidator.calculateCurrentEcosystemStatistics(seventyByOneHundredGridState);
        Set<String> activeAnimalSpecies = ecosystemValidator.evaluateActiveAnimals();
        assertFalse(
            activeAnimalSpecies.contains("Verdelopes"),
            "Verdelopes must not be active while Grass coverage rests below the 0.05 threshold.");

        placePlantSpeciesAcrossRowMajorCells(seventyByOneHundredGridState, GRASS_PLANT_INDEX, 100, 250);
        ecosystemValidator.calculateCurrentEcosystemStatistics(seventyByOneHundredGridState);
        activeAnimalSpecies = ecosystemValidator.evaluateActiveAnimals();
        assertTrue(
            activeAnimalSpecies.contains("Verdelopes"),
            "Verdelopes must become active the moment Grass coverage reaches exactly 0.05.");
        assertEquals(0.05, ecosystemValidator.currentEcosystemPlantSpeciesCoverageFraction(GRASS_PLANT_INDEX), 0.000001);
    }

    @Test
    void blueMossUnlocksOnlyWhenLoamcrawlersActiveAndGrassAndRoseBushCoverageThresholdsAreSimultaneouslyMet() throws IOException {
        EcosystemValidator ecosystemValidator = buildEcosystemValidatorWithOfficialLevelTwoRules();
        int roseBushPlantIndex = resolvePlantIndexMappedForOfficialSpeciesName("Rose Bush");
        int blueMossPlantIndex = resolvePlantIndexMappedForOfficialSpeciesName("Blue Moss");

        SimulationGridState seventyByOneHundredGridState = createSeventyByOneHundredGridState();
        placePlantSpeciesAcrossRowMajorCells(seventyByOneHundredGridState, GRASS_PLANT_INDEX, 0, 300);
        placePlantSpeciesAcrossRowMajorCells(seventyByOneHundredGridState, roseBushPlantIndex, 300, 100);

        ecosystemValidator.calculateCurrentEcosystemStatistics(seventyByOneHundredGridState);
        Set<String> activeAnimalSpecies = ecosystemValidator.evaluateActiveAnimals();
        assertTrue(
            activeAnimalSpecies.contains("Loamcrawlers"),
            "Loamcrawlers require AND Grass coverage at least 0.04 and at least 10 Rose Bush cells, both of which are satisfied here.");

        List<Integer> currentlyUnlockedPlantIndexes = ecosystemValidator.getCurrentlyUnlockedPlants();
        assertTrue(
            currentlyUnlockedPlantIndexes.contains(blueMossPlantIndex),
            "Blue Moss requires Loamcrawlers present AND Grass coverage greater than 0.03 AND Rose Bush coverage greater than 0.01; all three hold here.");

        terminatePlantSpeciesAcrossRowMajorCells(seventyByOneHundredGridState, 300, 80);
        ecosystemValidator.calculateCurrentEcosystemStatistics(seventyByOneHundredGridState);
        activeAnimalSpecies = ecosystemValidator.evaluateActiveAnimals();
        assertTrue(
            activeAnimalSpecies.contains("Loamcrawlers"),
            "Loamcrawlers must remain active after Rose Bush shrinks below the coverage threshold, isolating the Rose Bush variable.");

        currentlyUnlockedPlantIndexes = ecosystemValidator.getCurrentlyUnlockedPlants();
        assertFalse(
            currentlyUnlockedPlantIndexes.contains(blueMossPlantIndex),
            "Blue Moss must no longer be unlocked once Rose Bush coverage drops to 20 of 7000 cells (0.0029), below the 0.01 threshold.");
    }

    @Test
    void droughtAndRainWeatherEventsToggleCrystalCactusAndMireBloomUnlocksOnAndOff() throws IOException {
        EcosystemValidator ecosystemValidator = buildEcosystemValidatorWithOfficialLevelTwoRules();
        int crystalCactusPlantIndex = resolvePlantIndexMappedForOfficialSpeciesName("Crystal Cactus");
        int mireBloomPlantIndex = resolvePlantIndexMappedForOfficialSpeciesName("Mire Bloom");
        int blueMossPlantIndex = resolvePlantIndexMappedForOfficialSpeciesName("Blue Moss");

        SimulationGridState seventyByOneHundredGridState = createSeventyByOneHundredGridState();
        placePlantSpeciesAcrossRowMajorCells(seventyByOneHundredGridState, GRASS_PLANT_INDEX, 0, 300);
        placePlantSpeciesAcrossRowMajorCells(seventyByOneHundredGridState, blueMossPlantIndex, 300, 375);

        ecosystemValidator.calculateCurrentEcosystemStatistics(seventyByOneHundredGridState);
        List<Integer> currentlyUnlockedPlantIndexes = ecosystemValidator.getCurrentlyUnlockedPlants();
        assertFalse(currentlyUnlockedPlantIndexes.contains(crystalCactusPlantIndex), "Crystal Cactus must be locked while no Drought event is active.");
        assertFalse(currentlyUnlockedPlantIndexes.contains(mireBloomPlantIndex), "Mire Bloom must be locked while no Rain event is active.");

        ecosystemValidator.markWeatherEventActive("Drought");
        currentlyUnlockedPlantIndexes = ecosystemValidator.getCurrentlyUnlockedPlants();
        assertTrue(currentlyUnlockedPlantIndexes.contains(crystalCactusPlantIndex), "Drought must satisfy the Crystal Cactus event requirement.");
        assertFalse(currentlyUnlockedPlantIndexes.contains(mireBloomPlantIndex), "Mire Bloom must stay locked under Drought because it requires Rain.");

        ecosystemValidator.clearWeatherEventActive("Drought");
        ecosystemValidator.markWeatherEventActive("Rain");
        currentlyUnlockedPlantIndexes = ecosystemValidator.getCurrentlyUnlockedPlants();
        assertFalse(currentlyUnlockedPlantIndexes.contains(crystalCactusPlantIndex), "Clearing Drought must lock Crystal Cactus again.");
        assertTrue(currentlyUnlockedPlantIndexes.contains(mireBloomPlantIndex), "Rain must satisfy the Mire Bloom event requirement.");

        ecosystemValidator.clearWeatherEventActive("Rain");
        currentlyUnlockedPlantIndexes = ecosystemValidator.getCurrentlyUnlockedPlants();
        assertFalse(currentlyUnlockedPlantIndexes.contains(mireBloomPlantIndex), "Clearing Rain must lock Mire Bloom again.");
    }

    private EcosystemValidator buildEcosystemValidatorWithOfficialLevelTwoRules() throws IOException {
        return new EcosystemValidator(parseAllAnimalSpecies(), parseAllPlantUnlockRules());
    }

    private List<AnimalSpecies> parseAllAnimalSpecies() throws IOException {
        return readJsonResourceList("/animals.json", new TypeReference<List<AnimalSpecies>>() { });
    }

    private List<PlantUnlockRule> parseAllPlantUnlockRules() throws IOException {
        return readJsonResourceList("/plant_unlock_conditions.json", new TypeReference<List<PlantUnlockRule>>() { });
    }

    private <T> List<T> readJsonResourceList(String resourcePath, TypeReference<List<T>> jsonListTypeReference) throws IOException {
        InputStream resourceInputStream = Objects.requireNonNull(
            getClass().getResourceAsStream(resourcePath),
            "Unable to locate test resource [" + resourcePath + "] on the classpath.");
        return new ObjectMapper().readValue(resourceInputStream, jsonListTypeReference);
    }

    private int resolvePlantIndexMappedForOfficialSpeciesName(String officialPlantSpeciesName) {
        Integer mappedPlantIndex = EcosystemValidator.DEFAULT_LEVEL_TWO_PLANT_SPECIES_NAME_TO_INDEX_MAPPING.get(officialPlantSpeciesName);
        if (mappedPlantIndex == null) {
            throw new AssertionError(
                "The official plant species name [" + officialPlantSpeciesName + "] is missing from the default Level Two mapping.");
        }
        return mappedPlantIndex;
    }

    private SimulationGridState createSeventyByOneHundredGridState() {
        LevelState seventyByOneHundredLevelState = new LevelState(
            true,
            EcosystemValidator.DEFAULT_LEVEL_TWO_VERTICAL_ROW_COORDINATE_COUNT,
            EcosystemValidator.DEFAULT_LEVEL_TWO_HORIZONTAL_COLUMN_COORDINATE_COUNT,
            0,
            List.of(),
            List.of());
        return new SimulationGridState(seventyByOneHundredLevelState);
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

    private void terminatePlantSpeciesAcrossRowMajorCells(SimulationGridState simulationGridState, int startingCellOrdinal, int cellCountToTerminate) {
        int horizontalColumnCoordinateCount = simulationGridState.horizontalColumnCoordinateCount();
        for (int cellTerminationOffset = 0; cellTerminationOffset < cellCountToTerminate; cellTerminationOffset++) {
            int targetCellOrdinal = startingCellOrdinal + cellTerminationOffset;
            int targetVerticalRowCoordinate = targetCellOrdinal / horizontalColumnCoordinateCount;
            int targetHorizontalColumnCoordinate = targetCellOrdinal % horizontalColumnCoordinateCount;
            simulationGridState.terminatePlantAt(targetVerticalRowCoordinate, targetHorizontalColumnCoordinate);
        }
    }
}