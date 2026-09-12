package com.photospheria.engine.level1.simulation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photospheria.engine.level1.configuration.LevelState;
import com.photospheria.engine.level1.configuration.SimulationCommand;
import com.photospheria.engine.level2.ecosystem.AnimalSpecies;
import com.photospheria.engine.level2.ecosystem.EcosystemValidator;
import com.photospheria.engine.level2.ecosystem.PlantUnlockRule;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class SimulationOptimizer {

    public static final long DEFAULT_OPTIMIZATION_SEED_COUNT = 1000L;
    public static final long MINIMUM_REQUIRED_OPTIMIZATION_SEED_COUNT = 1000L;
    public static final int DEFAULT_STARTER_PLANT_INDEX = 1;
    public static final int STARTER_SEEDLING_CANDIDATE_PATH_COUNT = 8;
    public static final int DEFAULT_SPREAD_PROPAGATION_RADIUS = 1;
    public static final String DEFAULT_SPREAD_PROPAGATION_GEOMETRY = "VonNeumann";
    public static final int DEFAULT_TIME_REQUIRED_TO_REACH_MATURITY_IN_TICKS = 1;
    public static final boolean DEFAULT_DOES_PLANT_POSSESS_NO_WINTER_SPREAD_WEAKNESS = false;
    public static final int SCORE_MULTIPLIER_PER_LIVING_PLANT = 100;

    public static final int MAXIMUM_PLACEMENT_ACTIONS_PER_TICK = 20;
    public static final int MAXIMUM_PLACEMENT_ATTEMPT_COUNT_PER_TICK = 1000;
    public static final double PROBABILITY_WEIGHT_FOR_NEWLY_UNLOCKED_SPECIES_PLACEMENT_SELECTION = 0.80;
    public static final String LEVEL_TWO_EVENT_COMMAND_TYPE = "event";

    public static final List<Integer> LEVEL_TWO_STARTER_PLANT_INDEXES = List.of(
        requirePlantIndexMappedForOfficialSpeciesName("Grass"),
        requirePlantIndexMappedForOfficialSpeciesName("Rose Bush"),
        requirePlantIndexMappedForOfficialSpeciesName("Blue Moss"),
        requirePlantIndexMappedForOfficialSpeciesName("Crimson Vine"),
        requirePlantIndexMappedForOfficialSpeciesName("Dwarf Sunflower"));

    private static final String DEFAULT_INPUT_FILE_PATH = "shared_data/level_one_state.json";
    private static final String DEFAULT_LEVEL_ONE_OUTPUT_FILE_PATH = "level_1_greenhouse/outputs/solution.json";
    private static final String DEFAULT_LEVEL_TWO_OUTPUT_FILE_PATH = "level_2_garden/outputs/solution.json";
    private static final String ANIMAL_SPECIES_DATA_RESOURCE_PATH = "/animals.json";
    private static final String PLANT_UNLOCK_RULES_DATA_RESOURCE_PATH = "/plant_unlock_conditions.json";

    public static void main(String[] args) {
        try {
            String inputFilePath = resolveArgumentOrDefault(args, 0, DEFAULT_INPUT_FILE_PATH);
            long optimizationSeedCount = parseOptimizationSeedCount(
                resolveArgumentOrDefault(args, 2, Long.toString(DEFAULT_OPTIMIZATION_SEED_COUNT)));

            File inputStateFile = resolveExistingInputFile(inputFilePath);
            LevelState levelState = readLevelStateFromFile(inputStateFile);

            String requestedOutputFilePath = resolveArgumentOrDefault(args, 1, null);
            String defaultOutputFilePath = levelState.animalsEnabled()
                ? DEFAULT_LEVEL_TWO_OUTPUT_FILE_PATH
                : DEFAULT_LEVEL_ONE_OUTPUT_FILE_PATH;
            File outputStateFile = resolveRepositoryRootRelativeFile(
                requestedOutputFilePath != null ? requestedOutputFilePath : defaultOutputFilePath);

            OptimizationResult optimizationResult = optimize(levelState, optimizationSeedCount, outputStateFile);

            System.out.println("SimulationOptimizer completed successfully.");
            System.out.println("  Input state file               : " + inputStateFile.getAbsolutePath());
            System.out.println("  Output solution file           : " + optimizationResult.outputSolutionFile().getAbsolutePath());
            System.out.println("  Optimization seed count        : " + optimizationSeedCount);
            System.out.println("  Winning optimization seed      : " + optimizationResult.winningOptimizationSeed());
            System.out.println("  Final deterministic score      : " + optimizationResult.finalScore());
            System.out.println("  Final living plant count       : " + optimizationResult.finalLivingPlantCount());
            System.out.println("  Final total nutrient points    : " + optimizationResult.finalTotalNutrientPoints());
            System.out.println("  Recorded placement actions     : " + optimizationResult.placementActionCount());
        } catch (IOException ioException) {
            System.err.println("SimulationOptimizer failed: " + ioException.getMessage());
            System.exit(1);
        }
    }

    public static OptimizationResult optimize(LevelState levelState, long optimizationSeedCount, File outputSolutionFile) throws IOException {
        if (levelState == null) {
            throw new IllegalArgumentException("LevelState cannot be null.");
        }
        if (optimizationSeedCount < MINIMUM_REQUIRED_OPTIMIZATION_SEED_COUNT) {
            throw new IllegalArgumentException(
                "Optimization seed count must run at least [" + MINIMUM_REQUIRED_OPTIMIZATION_SEED_COUNT
                    + "] distinct seed iterations; received [" + optimizationSeedCount + "].");
        }
        if (outputSolutionFile == null) {
            throw new IllegalArgumentException("Output solution file cannot be null.");
        }

        boolean isDynamicPlantUnlockLevel = levelState.animalsEnabled();
        EcosystemValidator ecosystemValidator = isDynamicPlantUnlockLevel ? loadEcosystemValidatorForLevelTwo() : null;

        SimulationRunScore bestScoringRun = null;
        for (long optimizationSeedIndex = 1; optimizationSeedIndex <= optimizationSeedCount; optimizationSeedIndex++) {
            SimulationRunScore candidateRunScore = isDynamicPlantUnlockLevel
                ? simulateDeterministicRunWithDynamicPlacementPools(levelState, optimizationSeedIndex, ecosystemValidator)
                : simulateDeterministicRun(levelState, optimizationSeedIndex);
            if (bestScoringRun == null || candidateRunScore.finalScore() > bestScoringRun.finalScore()) {
                bestScoringRun = candidateRunScore;
            }
        }

        writeSolutionFile(outputSolutionFile, levelState, optimizationSeedCount, bestScoringRun);
        return new OptimizationResult(
            bestScoringRun.winningOptimizationSeedIndex(),
            bestScoringRun.finalScore(),
            bestScoringRun.finalLivingPlantCount(),
            bestScoringRun.finalTotalNutrientPoints(),
            bestScoringRun.placementActionsRecord().size(),
            outputSolutionFile);
    }

    public static SimulationGridState createSimulationGridStateFromLevelState(LevelState levelState) {
        if (levelState == null) {
            throw new IllegalArgumentException("LevelState cannot be null.");
        }
        return new SimulationGridState(levelState);
    }

    public static EcosystemValidator loadEcosystemValidatorForLevelTwo() throws IOException {
        List<AnimalSpecies> parsedAnimalSpecies = readJsonResourceList(
            ANIMAL_SPECIES_DATA_RESOURCE_PATH,
            new TypeReference<List<AnimalSpecies>>() { });
        List<PlantUnlockRule> parsedPlantUnlockRules = readJsonResourceList(
            PLANT_UNLOCK_RULES_DATA_RESOURCE_PATH,
            new TypeReference<List<PlantUnlockRule>>() { });
        return new EcosystemValidator(parsedAnimalSpecies, parsedPlantUnlockRules);
    }

    public static void applyScheduledCommandsForCurrentTick(
            List<SimulationCommand> simulationCommands,
            int currentSimulationTick,
            EcosystemValidator ecosystemValidator) {

        if (simulationCommands == null) {
            throw new IllegalArgumentException("Simulation commands list cannot be null.");
        }
        if (ecosystemValidator == null) {
            throw new IllegalArgumentException("Ecosystem validator cannot be null.");
        }
        for (SimulationCommand simulationCommand : simulationCommands) {
            if (LEVEL_TWO_EVENT_COMMAND_TYPE.equals(simulationCommand.commandType())
                && simulationCommand.executionTick() == currentSimulationTick) {
                String weatherEventName = simulationCommand.eventNamePresent().orElseThrow(() -> new IllegalArgumentException(
                    "Level Two event command at execution tick [" + currentSimulationTick + "] must declare a non-blank event name."));
                ecosystemValidator.markWeatherEventActive(weatherEventName);
            }
        }
    }

    public static Set<Integer> refreshCurrentlyUnlockedPlantIndexesPool(
            EcosystemValidator ecosystemValidator,
            Set<Integer> currentlyUnlockedPlantIndexesPool,
            Set<Integer> probabilityWeightedNewlyUnlockedSpeciesIndexes) {

        if (ecosystemValidator == null) {
            throw new IllegalArgumentException("Ecosystem validator cannot be null.");
        }
        if (currentlyUnlockedPlantIndexesPool == null) {
            throw new IllegalArgumentException("Currently unlocked plant indexes pool cannot be null.");
        }
        if (probabilityWeightedNewlyUnlockedSpeciesIndexes == null) {
            throw new IllegalArgumentException("Probability weighted newly unlocked species indexes cannot be null.");
        }

        List<Integer> freshlyUnlockedPlantIndexes = ecosystemValidator.getCurrentlyUnlockedPlants();
        Set<Integer> newlyDiscoveredPlantIndexes = new LinkedHashSet<>();
        for (int freshlyUnlockedPlantIndex : freshlyUnlockedPlantIndexes) {
            if (currentlyUnlockedPlantIndexesPool.add(freshlyUnlockedPlantIndex)) {
                probabilityWeightedNewlyUnlockedSpeciesIndexes.add(freshlyUnlockedPlantIndex);
                newlyDiscoveredPlantIndexes.add(freshlyUnlockedPlantIndex);
            }
        }
        return Set.copyOf(newlyDiscoveredPlantIndexes);
    }

    static SimulationRunScore simulateDeterministicRun(LevelState levelState, long optimizationSeedIndex) {
        SimulationGridState simulationGridState = new SimulationGridState(levelState);
        List<PlantingAction> placementActionsRecord = new ArrayList<>();
        placeStarterPlantCandidatesAtDeterministicCoordinates(simulationGridState, optimizationSeedIndex, placementActionsRecord);

        SimulationTickEngine simulationTickEngine = new SimulationTickEngine(levelState);
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        for (int currentSimulationTick = 1; currentSimulationTick <= levelState.tickCount(); currentSimulationTick++) {
            simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(currentSimulationTick);
            biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
            executeSpreadPhaseForCurrentTick(simulationGridState, simulationTickEngine);
        }

        return finaliseSimulationRunScore(levelState, simulationGridState, optimizationSeedIndex, placementActionsRecord);
    }

    static SimulationRunScore simulateDeterministicRunWithDynamicPlacementPools(
            LevelState levelState,
            long optimizationSeedIndex,
            EcosystemValidator ecosystemValidator) {

        SimulationGridState simulationGridState = createSimulationGridStateFromLevelState(levelState);
        List<PlantingAction> placementActionsRecord = new ArrayList<>();
        SimulationTickEngine simulationTickEngine = new SimulationTickEngine(levelState);
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);
        Random deterministicPlacementRandom = new Random(optimizationSeedIndex * 1_000_003L + 7L);

        Set<Integer> currentlyUnlockedPlantIndexesPool = new LinkedHashSet<>(LEVEL_TWO_STARTER_PLANT_INDEXES);
        Set<Integer> probabilityWeightedNewlyUnlockedSpeciesIndexes = new LinkedHashSet<>();

        for (int currentSimulationTick = 1; currentSimulationTick <= levelState.tickCount(); currentSimulationTick++) {
            generateAndExecutePlacementActionsForCurrentTick(
                simulationGridState,
                deterministicPlacementRandom,
                currentlyUnlockedPlantIndexesPool,
                probabilityWeightedNewlyUnlockedSpeciesIndexes,
                currentSimulationTick,
                placementActionsRecord);

            simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(currentSimulationTick);
            applyScheduledCommandsForCurrentTick(levelState.simulationCommands(), currentSimulationTick, ecosystemValidator);
            biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
            executeSpreadPhaseForCurrentTick(simulationGridState, simulationTickEngine);

            ecosystemValidator.calculateCurrentEcosystemStatistics(simulationGridState);
            ecosystemValidator.evaluateActiveAnimals();
            refreshCurrentlyUnlockedPlantIndexesPool(
                ecosystemValidator,
                currentlyUnlockedPlantIndexesPool,
                probabilityWeightedNewlyUnlockedSpeciesIndexes);
        }

        return finaliseSimulationRunScore(levelState, simulationGridState, optimizationSeedIndex, placementActionsRecord);
    }

    private static SimulationRunScore finaliseSimulationRunScore(
            LevelState levelState,
            SimulationGridState simulationGridState,
            long optimizationSeedIndex,
            List<PlantingAction> placementActionsRecord) {

        int[][] finalPlantPopulationGrid = exportPlantPopulationGrid(simulationGridState);
        int[][] finalCellularNutrientGrid = exportCellularNutrientGrid(simulationGridState);
        int finalLivingPlantCount = countLivingPlants(finalPlantPopulationGrid);
        long finalTotalNutrientPoints = sumTotalNutrientPoints(finalCellularNutrientGrid);
        long finalScore = calculateOptimizationScore(finalLivingPlantCount, finalTotalNutrientPoints);

        return new SimulationRunScore(
            optimizationSeedIndex,
            finalScore,
            finalLivingPlantCount,
            finalTotalNutrientPoints,
            placementActionsRecord,
            finalPlantPopulationGrid,
            finalCellularNutrientGrid);
    }

    private static void generateAndExecutePlacementActionsForCurrentTick(
            SimulationGridState simulationGridState,
            Random deterministicPlacementRandom,
            Set<Integer> currentlyUnlockedPlantIndexesPool,
            Set<Integer> probabilityWeightedNewlyUnlockedSpeciesIndexes,
            int currentSimulationTick,
            List<PlantingAction> placementActionsRecord) {

        List<Integer> starterPlantIndexesAsList = LEVEL_TWO_STARTER_PLANT_INDEXES;
        List<Integer> currentlyUnlockedPlantIndexesAsList = List.copyOf(currentlyUnlockedPlantIndexesPool);
        List<Integer> probabilityWeightedNewlyUnlockedSpeciesIndexesAsList = List.copyOf(probabilityWeightedNewlyUnlockedSpeciesIndexes);

        int placementAttemptCount = 0;
        int successfulPlacementActionCount = 0;
        while (successfulPlacementActionCount < MAXIMUM_PLACEMENT_ACTIONS_PER_TICK
            && placementAttemptCount < MAXIMUM_PLACEMENT_ATTEMPT_COUNT_PER_TICK) {

            placementAttemptCount++;
            int candidateVerticalRowCoordinate = deterministicPlacementRandom.nextInt(simulationGridState.verticalRowCoordinateCount());
            int candidateHorizontalColumnCoordinate = deterministicPlacementRandom.nextInt(simulationGridState.horizontalColumnCoordinateCount());
            if (simulationGridState.plantIndexOfCell(candidateVerticalRowCoordinate, candidateHorizontalColumnCoordinate)
                != SimulationGridState.DEAD_PLANT_INDEX) {
                continue;
            }

            int selectedPlantIndex = selectPlantIndexForPlacementAction(
                deterministicPlacementRandom,
                starterPlantIndexesAsList,
                currentlyUnlockedPlantIndexesAsList,
                probabilityWeightedNewlyUnlockedSpeciesIndexesAsList);
            simulationGridState.registerNewPlantAt(
                candidateVerticalRowCoordinate,
                candidateHorizontalColumnCoordinate,
                selectedPlantIndex);
            placementActionsRecord.add(new PlantingAction(
                currentSimulationTick,
                candidateVerticalRowCoordinate,
                candidateHorizontalColumnCoordinate,
                selectedPlantIndex));
            successfulPlacementActionCount++;
        }
    }

    private static int selectPlantIndexForPlacementAction(
            Random deterministicPlacementRandom,
            List<Integer> starterPlantIndexesAsList,
            List<Integer> currentlyUnlockedPlantIndexesAsList,
            List<Integer> probabilityWeightedNewlyUnlockedSpeciesIndexesAsList) {

        if (probabilityWeightedNewlyUnlockedSpeciesIndexesAsList.isEmpty()) {
            return currentlyUnlockedPlantIndexesAsList.get(
                deterministicPlacementRandom.nextInt(currentlyUnlockedPlantIndexesAsList.size()));
        }

        double speciesSelectionRoll = deterministicPlacementRandom.nextDouble();
        if (speciesSelectionRoll < PROBABILITY_WEIGHT_FOR_NEWLY_UNLOCKED_SPECIES_PLACEMENT_SELECTION) {
            return probabilityWeightedNewlyUnlockedSpeciesIndexesAsList.get(
                deterministicPlacementRandom.nextInt(probabilityWeightedNewlyUnlockedSpeciesIndexesAsList.size()));
        }
        return starterPlantIndexesAsList.get(
            deterministicPlacementRandom.nextInt(starterPlantIndexesAsList.size()));
    }

    private static void placeStarterPlantCandidatesAtDeterministicCoordinates(
            SimulationGridState simulationGridState,
            long optimizationSeedIndex,
            List<PlantingAction> placementActionsRecord) {

        Random placementRandom = new Random(optimizationSeedIndex * 1_000_003L + 7L);
        int placedStarterSeedlingCandidates = 0;
        while (placedStarterSeedlingCandidates < STARTER_SEEDLING_CANDIDATE_PATH_COUNT) {
            int candidateVerticalRowCoordinate = placementRandom.nextInt(simulationGridState.verticalRowCoordinateCount());
            int candidateHorizontalColumnCoordinate = placementRandom.nextInt(simulationGridState.horizontalColumnCoordinateCount());
            if (simulationGridState.plantIndexOfCell(candidateVerticalRowCoordinate, candidateHorizontalColumnCoordinate)
                == SimulationGridState.DEAD_PLANT_INDEX) {
                simulationGridState.registerNewPlantAt(
                    candidateVerticalRowCoordinate,
                    candidateHorizontalColumnCoordinate,
                    DEFAULT_STARTER_PLANT_INDEX);
                placementActionsRecord.add(new PlantingAction(
                    0,
                    candidateVerticalRowCoordinate,
                    candidateHorizontalColumnCoordinate,
                    DEFAULT_STARTER_PLANT_INDEX));
                placedStarterSeedlingCandidates++;
            }
        }
    }

    private static void executeSpreadPhaseForCurrentTick(SimulationGridState simulationGridState, SimulationTickEngine simulationTickEngine) {
        List<int[]> currentLivingPlantCoordinates = new ArrayList<>();
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount(); verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount(); horizontalColumnCoordinate++) {
                if (simulationGridState.plantIndexOfCell(verticalRowCoordinate, horizontalColumnCoordinate)
                    != SimulationGridState.DEAD_PLANT_INDEX) {
                    currentLivingPlantCoordinates.add(new int[] { verticalRowCoordinate, horizontalColumnCoordinate });
                }
            }
        }
        for (int[] livingPlantCoordinate : currentLivingPlantCoordinates) {
            int sourceVerticalRowCoordinate = livingPlantCoordinate[0];
            int sourceHorizontalColumnCoordinate = livingPlantCoordinate[1];
            int sourcePlantIndex = simulationGridState.plantIndexOfCell(sourceVerticalRowCoordinate, sourceHorizontalColumnCoordinate);
            simulationTickEngine.executeSpreadForSinglePlant(
                sourceVerticalRowCoordinate,
                sourceHorizontalColumnCoordinate,
                DEFAULT_SPREAD_PROPAGATION_RADIUS,
                DEFAULT_SPREAD_PROPAGATION_GEOMETRY,
                DEFAULT_DOES_PLANT_POSSESS_NO_WINTER_SPREAD_WEAKNESS,
                DEFAULT_TIME_REQUIRED_TO_REACH_MATURITY_IN_TICKS,
                sourcePlantIndex);
        }
    }

    private static int[][] exportPlantPopulationGrid(SimulationGridState simulationGridState) {
        int[][] exportedPlantPopulationGrid = new int[simulationGridState.verticalRowCoordinateCount()][simulationGridState.horizontalColumnCoordinateCount()];
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount(); verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount(); horizontalColumnCoordinate++) {
                exportedPlantPopulationGrid[verticalRowCoordinate][horizontalColumnCoordinate] =
                    simulationGridState.plantIndexOfCell(verticalRowCoordinate, horizontalColumnCoordinate);
            }
        }
        return exportedPlantPopulationGrid;
    }

    private static int[][] exportCellularNutrientGrid(SimulationGridState simulationGridState) {
        int[][] exportedCellularNutrientGrid = new int[simulationGridState.verticalRowCoordinateCount()][simulationGridState.horizontalColumnCoordinateCount()];
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount(); verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount(); horizontalColumnCoordinate++) {
                exportedCellularNutrientGrid[verticalRowCoordinate][horizontalColumnCoordinate] =
                    simulationGridState.cellularNutrientPointsAt(verticalRowCoordinate, horizontalColumnCoordinate);
            }
        }
        return exportedCellularNutrientGrid;
    }

    private static int countLivingPlants(int[][] finalPlantPopulationGrid) {
        int livingPlantCount = 0;
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < finalPlantPopulationGrid.length; verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < finalPlantPopulationGrid[verticalRowCoordinate].length; horizontalColumnCoordinate++) {
                if (finalPlantPopulationGrid[verticalRowCoordinate][horizontalColumnCoordinate] != SimulationGridState.DEAD_PLANT_INDEX) {
                    livingPlantCount++;
                }
            }
        }
        return livingPlantCount;
    }

    private static long sumTotalNutrientPoints(int[][] finalCellularNutrientGrid) {
        long totalNutrientPoints = 0;
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < finalCellularNutrientGrid.length; verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < finalCellularNutrientGrid[verticalRowCoordinate].length; horizontalColumnCoordinate++) {
                totalNutrientPoints += finalCellularNutrientGrid[verticalRowCoordinate][horizontalColumnCoordinate];
            }
        }
        return totalNutrientPoints;
    }

    private static long calculateOptimizationScore(int finalLivingPlantCount, long finalTotalNutrientPoints) {
        return (long) finalLivingPlantCount * SCORE_MULTIPLIER_PER_LIVING_PLANT + finalTotalNutrientPoints;
    }

    private static void writeSolutionFile(
            File outputSolutionFile,
            LevelState levelState,
            long optimizationSeedCount,
            SimulationRunScore bestScoringRun) throws IOException {

        File parentOutputDirectory = outputSolutionFile.getParentFile();
        if (parentOutputDirectory != null && !parentOutputDirectory.exists()) {
            parentOutputDirectory.mkdirs();
        }

        Map<String, Object> solutionOutput = new LinkedHashMap<>();
        solutionOutput.put("level_number", levelState.animalsEnabled() ? 2 : 1);
        solutionOutput.put("deterministic_reproducibility", true);
        solutionOutput.put("total_simulation_ticks_advanced", levelState.tickCount());
        solutionOutput.put("optimization_seed_count_compared", optimizationSeedCount);
        solutionOutput.put("winning_optimization_seed", bestScoringRun.winningOptimizationSeedIndex());
        solutionOutput.put("final_score", bestScoringRun.finalScore());
        solutionOutput.put("final_living_plant_count", bestScoringRun.finalLivingPlantCount());
        solutionOutput.put("final_total_nutrient_points", bestScoringRun.finalTotalNutrientPoints());
        solutionOutput.put("placement_sequence", bestScoringRun.placementActionsRecord());
        solutionOutput.put("final_plant_population_grid", bestScoringRun.finalPlantPopulationGrid());
        solutionOutput.put("final_cellular_nutrient_grid", bestScoringRun.finalCellularNutrientGrid());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputSolutionFile, solutionOutput);
    }

    private static String resolveArgumentOrDefault(String[] args, int argumentIndex, String defaultValue) {
        return args.length > argumentIndex ? args[argumentIndex] : defaultValue;
    }

    private static long parseOptimizationSeedCount(String rawOptimizationSeedCount) {
        try {
            return Long.parseLong(rawOptimizationSeedCount);
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(
                "Optimization seed count must be a numeric value; received [" + rawOptimizationSeedCount + "].");
        }
    }

    private static LevelState readLevelStateFromFile(File inputStateFile) throws IOException {
        return new ObjectMapper().readValue(inputStateFile, LevelState.class);
    }

    private static <T> List<T> readJsonResourceList(String resourcePath, TypeReference<List<T>> jsonListTypeReference) throws IOException {
        InputStream resourceInputStream = SimulationOptimizer.class.getResourceAsStream(resourcePath);
        if (resourceInputStream == null) {
            throw new IllegalArgumentException("Unable to locate classpath resource [" + resourcePath + "].");
        }
        try (InputStream autoClosedResourceInputStream = resourceInputStream) {
            return new ObjectMapper().readValue(autoClosedResourceInputStream, jsonListTypeReference);
        }
    }

    private static int requirePlantIndexMappedForOfficialSpeciesName(String officialPlantSpeciesName) {
        Integer mappedPlantIndex = EcosystemValidator.DEFAULT_LEVEL_TWO_PLANT_SPECIES_NAME_TO_INDEX_MAPPING.get(officialPlantSpeciesName);
        if (mappedPlantIndex == null) {
            throw new IllegalArgumentException(
                "Official plant species name [" + officialPlantSpeciesName + "] is missing from the default Level Two mapping.");
        }
        return mappedPlantIndex;
    }

    private static File resolveExistingInputFile(String inputFilePath) {
        File directCandidateFile = new File(inputFilePath);
        if (directCandidateFile.isFile()) {
            return directCandidateFile;
        }
        File parentRelativeCandidateFile = new File("..", inputFilePath);
        if (parentRelativeCandidateFile.isFile()) {
            return parentRelativeCandidateFile;
        }
        throw new IllegalArgumentException(
            "Input state file could not be located at [" + directCandidateFile.getAbsolutePath()
                + "] or [" + parentRelativeCandidateFile.getAbsolutePath() + "].");
    }

    private static File resolveRepositoryRootRelativeFile(String outputFilePath) {
        File candidateFile = new File(outputFilePath);
        if (candidateFile.isAbsolute()) {
            return candidateFile;
        }
        boolean isRunningFromRepositoryRoot = new File("level_1_greenhouse").isDirectory();
        if (isRunningFromRepositoryRoot) {
            return candidateFile;
        }
        return new File("..", outputFilePath);
    }

    static record SimulationRunScore(
            long winningOptimizationSeedIndex,
            long finalScore,
            int finalLivingPlantCount,
            long finalTotalNutrientPoints,
            List<PlantingAction> placementActionsRecord,
            int[][] finalPlantPopulationGrid,
            int[][] finalCellularNutrientGrid) {
    }

    public record PlantingAction(
            @JsonProperty("tick") int executionTick,
            @JsonProperty("row") int verticalRowCoordinate,
            @JsonProperty("col") int horizontalColumnCoordinate,
            @JsonProperty("plant_index") int plantIndex) {
    }

    public record OptimizationResult(
            long winningOptimizationSeed,
            long finalScore,
            int finalLivingPlantCount,
            long finalTotalNutrientPoints,
            int placementActionCount,
            File outputSolutionFile) {
    }
}