package com.photospheria.engine.level1.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photospheria.engine.level1.configuration.LevelState;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.StructuredTaskScope;

public class HeuristicSimulationOptimizer {

    public static final long DEFAULT_OPTIMIZATION_SEED_COUNT = 1000L;
    public static final long MINIMUM_REQUIRED_OPTIMIZATION_SEED_COUNT = 1000L;
    public static final int STRUCTURED_SCOPE_SEED_BATCH_SIZE = 100;
    public static final int ROCK_ENVIRONMENTAL_TERRAIN_TYPE = 4;
    public static final int STONE_REED_PLANT_INDEX = 11;
    public static final int CRIMSON_VINE_PLANT_INDEX = 9;
    public static final int GRASS_PLANT_INDEX = 1;
    public static final int ROSE_BUSH_PLANT_INDEX = 2;
    public static final int MAXIMUM_PLACEMENT_ACTIONS_PER_TICK = SimulationOptimizer.MAXIMUM_PLACEMENT_ACTIONS_PER_TICK;
    public static final int MAXIMUM_PLACEMENT_ATTEMPT_COUNT_PER_TICK = SimulationOptimizer.MAXIMUM_PLACEMENT_ATTEMPT_COUNT_PER_TICK;
    public static final boolean ALL_KNOWN_PLANTS_POSSESS_NO_WINTER_SPREAD_WEAKNESS = false;
    public static final int FAST_FAIL_ENTROPY_EVALUATION_TICK = 100;
    public static final int FAST_FAIL_LIVING_PLANT_DIVERSITY_WEIGHT = 50;
    public static final int FAST_FAIL_MINIMUM_ENTROPY_SCORE_THRESHOLD = 350;
    public static final int DEFAULT_LEVEL_FOUR_VERTICAL_ROW_COORDINATE_COUNT =
        SimulationOptimizer.DEFAULT_LEVEL_FOUR_VERTICAL_ROW_COORDINATE_COUNT;
    public static final int DEFAULT_LEVEL_FOUR_HORIZONTAL_COLUMN_COORDINATE_COUNT =
        SimulationOptimizer.DEFAULT_LEVEL_FOUR_HORIZONTAL_COLUMN_COORDINATE_COUNT;

    public static final List<Integer> LEVEL_THREE_PLACEMENT_POOL_PLANT_INDEXES = List.of(
        GRASS_PLANT_INDEX,
        ROSE_BUSH_PLANT_INDEX,
        CRIMSON_VINE_PLANT_INDEX,
        STONE_REED_PLANT_INDEX);

    private static final String DEFAULT_INPUT_FILE_PATH = "shared_data/level_four_state.json";
    private static final String DEFAULT_LEVEL_FOUR_OUTPUT_FILE_PATH = "level_4_mountain/outputs/solution.json";

    public static void main(String[] args) {
        try {
            String inputFilePath = resolveArgumentOrDefault(args, 0, DEFAULT_INPUT_FILE_PATH);
            long optimizationSeedCount = parseOptimizationSeedCount(
                resolveArgumentOrDefault(args, 2, Long.toString(DEFAULT_OPTIMIZATION_SEED_COUNT)));

            File inputStateFile = resolveExistingInputFile(inputFilePath);
            LevelState levelState = readLevelStateFromFile(inputStateFile);

            String requestedOutputFilePath = resolveArgumentOrDefault(args, 1, null);
            File outputStateFile = resolveRepositoryRootRelativeFile(
                requestedOutputFilePath != null ? requestedOutputFilePath : DEFAULT_LEVEL_FOUR_OUTPUT_FILE_PATH);

            HeuristicSimulationRunScore winningHeuristicRun = optimizeHeuristicallyWithStructuredConcurrency(levelState, optimizationSeedCount);
            writeSolutionFile(outputStateFile, levelState, optimizationSeedCount, winningHeuristicRun);

            System.out.println("HeuristicSimulationOptimizer completed successfully.");
            System.out.println("  Input state file               : " + inputStateFile.getAbsolutePath());
            System.out.println("  Output solution file           : " + outputStateFile.getAbsolutePath());
            System.out.println("  Optimization seed count        : " + optimizationSeedCount);
            System.out.println("  Winning optimization seed      : " + winningHeuristicRun.winningOptimizationSeedIndex());
            System.out.println("  Final deterministic score      : " + winningHeuristicRun.finalScore());
            System.out.println("  Final living plant count       : " + winningHeuristicRun.finalLivingPlantCount());
            System.out.println("  Final total nutrient points    : " + winningHeuristicRun.finalTotalNutrientPoints());
            System.out.println("  Recorded placement actions     : " + winningHeuristicRun.placementActionsRecord().size());
        } catch (IOException | InterruptedException executionFailureException) {
            System.err.println("HeuristicSimulationOptimizer failed: " + executionFailureException.getMessage());
            System.exit(1);
        }
    }

    static HeuristicSimulationRunScore optimizeHeuristicallyWithStructuredConcurrency(
            LevelState levelState,
            long optimizationSeedCount) throws InterruptedException {

        if (levelState == null) {
            throw new IllegalArgumentException("LevelState cannot be null.");
        }
        if (optimizationSeedCount < MINIMUM_REQUIRED_OPTIMIZATION_SEED_COUNT) {
            throw new IllegalArgumentException(
                "Optimization seed count must run at least [" + MINIMUM_REQUIRED_OPTIMIZATION_SEED_COUNT
                    + "] distinct seed iterations; received [" + optimizationSeedCount + "].");
        }

        HeuristicSimulationRunScore bestScoringHeuristicRun = null;
        HeuristicSimulationRunScore bestScoringFastFailedHeuristicRun = null;
        for (long batchStartSeedIndex = 1L;
            batchStartSeedIndex <= optimizationSeedCount;
            batchStartSeedIndex += STRUCTURED_SCOPE_SEED_BATCH_SIZE) {

            long batchEndSeedIndexExclusive = Math.min(
                batchStartSeedIndex + STRUCTURED_SCOPE_SEED_BATCH_SIZE,
                optimizationSeedCount + 1L);
            List<HeuristicSimulationRunScore> batchHeuristicRunScores = runSeedBatchWithinStructuredTaskScope(
                levelState,
                batchStartSeedIndex,
                batchEndSeedIndexExclusive);
            for (HeuristicSimulationRunScore candidateHeuristicRunScore : batchHeuristicRunScores) {
                if (candidateHeuristicRunScore.wasFastFailedSimulation()) {
                    if (bestScoringFastFailedHeuristicRun == null
                        || candidateHeuristicRunScore.finalScore() > bestScoringFastFailedHeuristicRun.finalScore()) {
                        bestScoringFastFailedHeuristicRun = candidateHeuristicRunScore;
                    }
                    continue;
                }
                if (bestScoringHeuristicRun == null
                    || candidateHeuristicRunScore.finalScore() > bestScoringHeuristicRun.finalScore()) {
                    bestScoringHeuristicRun = candidateHeuristicRunScore;
                }
            }
        }
        return bestScoringHeuristicRun != null
            ? bestScoringHeuristicRun
            : bestScoringFastFailedHeuristicRun;
    }

    private static List<HeuristicSimulationRunScore> runSeedBatchWithinStructuredTaskScope(
            LevelState levelState,
            long batchStartSeedIndexInclusive,
            long batchEndSeedIndexExclusive) throws InterruptedException {

        try (StructuredTaskScope<HeuristicSimulationRunScore, List<HeuristicSimulationRunScore>> concurrentVirtualThreadTaskScope =
            StructuredTaskScope.<HeuristicSimulationRunScore, List<HeuristicSimulationRunScore>>open(
                StructuredTaskScope.Joiner.<HeuristicSimulationRunScore>allSuccessfulOrThrow(),
                structuredTaskScopeConfiguration -> structuredTaskScopeConfiguration.withThreadFactory(
                    Thread.ofVirtual().factory()))) {

            for (long seedIndex = batchStartSeedIndexInclusive; seedIndex < batchEndSeedIndexExclusive; seedIndex++) {
                final long capturedSeedIndex = seedIndex;
                concurrentVirtualThreadTaskScope.fork(
                    () -> simulateHeuristicDeterministicRun(levelState, capturedSeedIndex));
            }
            return concurrentVirtualThreadTaskScope.join();
        }
    }

    static HeuristicSimulationRunScore simulateHeuristicDeterministicRun(LevelState levelState, long optimizationSeedIndex) {
        SimulationGridState simulationGridState = SimulationOptimizer.createSimulationGridStateFromLevelState(levelState);
        List<SimulationOptimizer.PlantingAction> placementActionsRecord = new ArrayList<>();
        SimulationTickEngine simulationTickEngine = new SimulationTickEngine(levelState);
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);
        Random deterministicPlacementRandom = new Random(optimizationSeedIndex * 1_000_003L + 7L);

        List<Coordinate> preComputedCoordinatesAdjacentToRockTerrain =
            computePreComputedCoordinatesAdjacentToRockTerrain(simulationGridState);
        List<Coordinate> preComputedCoordinatesWithCultivableSoilTopography =
            computePreComputedCoordinatesWithCultivableSoilTopography(simulationGridState);

        boolean didSimulationFastFailBelowBaselineEntropy = false;
        for (int currentSimulationTick = 1; currentSimulationTick <= levelState.tickCount(); currentSimulationTick++) {
            generateAndExecuteHeuristicPlacementActionsForCurrentTick(
                simulationGridState,
                deterministicPlacementRandom,
                currentSimulationTick,
                placementActionsRecord,
                preComputedCoordinatesAdjacentToRockTerrain,
                preComputedCoordinatesWithCultivableSoilTopography);
            simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(currentSimulationTick);
            biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
            executeSpreadPhaseForCurrentTick(simulationGridState, simulationTickEngine);

            if (currentSimulationTick == FAST_FAIL_ENTROPY_EVALUATION_TICK
                && shouldFastFailSimulationForBelowBaselineEntropy(
                calculateLivingPlantEntropyScore(simulationGridState))) {
                didSimulationFastFailBelowBaselineEntropy = true;
                break;
            }
        }

        int[][] finalPlantPopulationGrid = exportPlantPopulationGrid(simulationGridState);
        int[][] finalCellularNutrientGrid = exportCellularNutrientGrid(simulationGridState);
        int finalLivingPlantCount = countLivingPlants(finalPlantPopulationGrid);
        long finalTotalNutrientPoints = sumTotalNutrientPoints(finalCellularNutrientGrid);
        long finalScore = calculateOptimizationScore(finalLivingPlantCount, finalTotalNutrientPoints);

        return new HeuristicSimulationRunScore(
            optimizationSeedIndex,
            finalScore,
            finalLivingPlantCount,
            finalTotalNutrientPoints,
            placementActionsRecord,
            finalPlantPopulationGrid,
            finalCellularNutrientGrid,
            didSimulationFastFailBelowBaselineEntropy);
    }

    static long calculateLivingPlantEntropyScore(SimulationGridState simulationGridState) {
        if (simulationGridState == null) {
            throw new IllegalArgumentException("SimulationGridState cannot be null.");
        }
        Set<Integer> livingPlantSpeciesIndexes = new HashSet<>();
        int livingPlantCount = 0;
        for (int verticalRowCoordinate = 0;
            verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount();
            verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0;
                horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount();
                horizontalColumnCoordinate++) {
                int plantIndexAtCell = simulationGridState.plantIndexOfCell(verticalRowCoordinate, horizontalColumnCoordinate);
                if (plantIndexAtCell != SimulationGridState.DEAD_PLANT_INDEX) {
                    livingPlantCount++;
                    livingPlantSpeciesIndexes.add(plantIndexAtCell);
                }
            }
        }
        return livingPlantCount
            + (long) livingPlantSpeciesIndexes.size() * FAST_FAIL_LIVING_PLANT_DIVERSITY_WEIGHT;
    }

    static boolean shouldFastFailSimulationForBelowBaselineEntropy(long livingPlantEntropyScore) {
        return livingPlantEntropyScore < FAST_FAIL_MINIMUM_ENTROPY_SCORE_THRESHOLD;
    }

    public static List<Coordinate> computePreComputedCoordinatesAdjacentToRockTerrain(SimulationGridState simulationGridState) {
        if (simulationGridState == null) {
            throw new IllegalArgumentException("SimulationGridState cannot be null.");
        }
        Set<Integer> rockAdjacentCoordinateKeys = new HashSet<>();
        for (int verticalRowCoordinate = 0;
            verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount();
            verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0;
                horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount();
                horizontalColumnCoordinate++) {
                if (simulationGridState.environmentalTerrainTypeAt(verticalRowCoordinate, horizontalColumnCoordinate)
                    == ROCK_ENVIRONMENTAL_TERRAIN_TYPE) {
                    collectAvailableRockAdjacentCoordinateKeysAroundCell(
                        simulationGridState,
                        verticalRowCoordinate,
                        horizontalColumnCoordinate,
                        rockAdjacentCoordinateKeys);
                }
            }
        }
        return decodeCoordinateKeysIntoCoordinateList(simulationGridState, rockAdjacentCoordinateKeys);
    }

    public static List<Coordinate> computePreComputedCoordinatesWithCultivableSoilTopography(SimulationGridState simulationGridState) {
        if (simulationGridState == null) {
            throw new IllegalArgumentException("SimulationGridState cannot be null.");
        }
        List<Coordinate> preComputedCoordinatesWithCultivableSoilTopography = new ArrayList<>();
        for (int verticalRowCoordinate = 0;
            verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount();
            verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0;
                horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount();
                horizontalColumnCoordinate++) {
                if (simulationGridState.environmentalSoilTypeAt(verticalRowCoordinate, horizontalColumnCoordinate) > 0
                    && simulationGridState.environmentalTerrainTypeAt(verticalRowCoordinate, horizontalColumnCoordinate)
                    != ROCK_ENVIRONMENTAL_TERRAIN_TYPE) {
                    preComputedCoordinatesWithCultivableSoilTopography.add(
                        new Coordinate(verticalRowCoordinate, horizontalColumnCoordinate));
                }
            }
        }
        return List.copyOf(preComputedCoordinatesWithCultivableSoilTopography);
    }

    static FrontierCoordinateSet buildFreshFrontierCoordinateSet(SimulationGridState simulationGridState) {
        FrontierCoordinateSet frontierCoordinateSet = new FrontierCoordinateSet();
        for (int verticalRowCoordinate = 0;
            verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount();
            verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0;
                horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount();
                horizontalColumnCoordinate++) {
                if (simulationGridState.plantIndexOfCell(verticalRowCoordinate, horizontalColumnCoordinate)
                    != SimulationGridState.DEAD_PLANT_INDEX) {
                    collectAvailableDeadNeighbourhoodCoordinateKeysAroundCell(
                        simulationGridState,
                        verticalRowCoordinate,
                        horizontalColumnCoordinate,
                        frontierCoordinateSet);
                }
            }
        }
        return frontierCoordinateSet;
    }

    static Coordinate selectTargetCoordinateForPlantPlacement(
            int selectedPlantIndex,
            SimulationGridState simulationGridState,
            Random deterministicPlacementRandom,
            List<Coordinate> preComputedCoordinatesAdjacentToRockTerrain,
            List<Coordinate> preComputedCoordinatesWithCultivableSoilTopography,
            FrontierCoordinateSet frontierCoordinateSet) {

        return switch (Integer.valueOf(selectedPlantIndex)) {
            case Integer stoneReedPlantIndex when stoneReedPlantIndex == STONE_REED_PLANT_INDEX ->
                selectRandomAvailableCoordinateFromPreComputedCoordinateList(
                    preComputedCoordinatesAdjacentToRockTerrain,
                    simulationGridState,
                    deterministicPlacementRandom);
            case Integer crimsonVinePlantIndex when crimsonVinePlantIndex == CRIMSON_VINE_PLANT_INDEX ->
                frontierCoordinateSet.selectRandomAvailableCoordinate(simulationGridState, deterministicPlacementRandom);
            default ->
                selectRandomAvailableCoordinateFromPreComputedCoordinateList(
                    preComputedCoordinatesWithCultivableSoilTopography,
                    simulationGridState,
                    deterministicPlacementRandom);
        };
    }

    static Coordinate selectTargetCoordinateForPlantPlacement(
            int selectedPlantIndex,
            SimulationGridState simulationGridState,
            Random deterministicPlacementRandom,
            List<Coordinate> preComputedCoordinatesAdjacentToRockTerrain,
            List<Coordinate> preComputedCoordinatesWithCultivableSoilTopography) {

        return selectTargetCoordinateForPlantPlacement(
            selectedPlantIndex,
            simulationGridState,
            deterministicPlacementRandom,
            preComputedCoordinatesAdjacentToRockTerrain,
            preComputedCoordinatesWithCultivableSoilTopography,
            buildFreshFrontierCoordinateSet(simulationGridState));
    }

    private static void generateAndExecuteHeuristicPlacementActionsForCurrentTick(
            SimulationGridState simulationGridState,
            Random deterministicPlacementRandom,
            int currentSimulationTick,
            List<SimulationOptimizer.PlantingAction> placementActionsRecord,
            List<Coordinate> preComputedCoordinatesAdjacentToRockTerrain,
            List<Coordinate> preComputedCoordinatesWithCultivableSoilTopography) {

        FrontierCoordinateSet frontierCoordinateSet = buildFreshFrontierCoordinateSet(simulationGridState);
        int placementAttemptCount = 0;
        int successfulPlacementActionCount = 0;
        while (successfulPlacementActionCount < MAXIMUM_PLACEMENT_ACTIONS_PER_TICK
            && placementAttemptCount < MAXIMUM_PLACEMENT_ATTEMPT_COUNT_PER_TICK) {

            placementAttemptCount++;
            int selectedPlantIndex = selectRandomPlantIndexFromPlacementPool(deterministicPlacementRandom);
            Coordinate targetedPlacementCoordinate = selectTargetCoordinateForPlantPlacement(
                selectedPlantIndex,
                simulationGridState,
                deterministicPlacementRandom,
                preComputedCoordinatesAdjacentToRockTerrain,
                preComputedCoordinatesWithCultivableSoilTopography,
                frontierCoordinateSet);
            if (targetedPlacementCoordinate == null) {
                continue;
            }

            simulationGridState.registerNewPlantAt(
                targetedPlacementCoordinate.verticalRowCoordinate(),
                targetedPlacementCoordinate.horizontalColumnCoordinate(),
                selectedPlantIndex);
            collectAvailableDeadNeighbourhoodCoordinateKeysAroundCell(
                simulationGridState,
                targetedPlacementCoordinate.verticalRowCoordinate(),
                targetedPlacementCoordinate.horizontalColumnCoordinate(),
                frontierCoordinateSet);
            placementActionsRecord.add(new SimulationOptimizer.PlantingAction(
                currentSimulationTick,
                targetedPlacementCoordinate.verticalRowCoordinate(),
                targetedPlacementCoordinate.horizontalColumnCoordinate(),
                selectedPlantIndex));
            successfulPlacementActionCount++;
        }
    }

    private static int selectRandomPlantIndexFromPlacementPool(Random deterministicPlacementRandom) {
        return LEVEL_THREE_PLACEMENT_POOL_PLANT_INDEXES.get(
            deterministicPlacementRandom.nextInt(LEVEL_THREE_PLACEMENT_POOL_PLANT_INDEXES.size()));
    }

    private static Coordinate selectRandomAvailableCoordinateFromPreComputedCoordinateList(
            List<Coordinate> preComputedCoordinates,
            SimulationGridState simulationGridState,
            Random deterministicPlacementRandom) {

        if (preComputedCoordinates.isEmpty()) {
            return null;
        }
        int startingCoordinateIndex = deterministicPlacementRandom.nextInt(preComputedCoordinates.size());
        for (int candidateOffset = 0; candidateOffset < preComputedCoordinates.size(); candidateOffset++) {
            Coordinate candidateCoordinate = preComputedCoordinates.get(
                (startingCoordinateIndex + candidateOffset) % preComputedCoordinates.size());
            if (simulationGridState.plantIndexOfCell(
                candidateCoordinate.verticalRowCoordinate(),
                candidateCoordinate.horizontalColumnCoordinate()) == SimulationGridState.DEAD_PLANT_INDEX) {
                return candidateCoordinate;
            }
        }
        return null;
    }

    private static void collectAvailableRockAdjacentCoordinateKeysAroundCell(
            SimulationGridState simulationGridState,
            int rockVerticalRowCoordinate,
            int rockHorizontalColumnCoordinate,
            Set<Integer> rockAdjacentCoordinateKeys) {

        int horizontalColumnCoordinateCount = simulationGridState.horizontalColumnCoordinateCount();
        for (int verticalRowOffset = -1; verticalRowOffset <= 1; verticalRowOffset++) {
            for (int horizontalColumnOffset = -1; horizontalColumnOffset <= 1; horizontalColumnOffset++) {
                if (Math.abs(verticalRowOffset) + Math.abs(horizontalColumnOffset) != 1) {
                    continue;
                }
                int neighbourVerticalRowCoordinate = rockVerticalRowCoordinate + verticalRowOffset;
                int neighbourHorizontalColumnCoordinate = rockHorizontalColumnCoordinate + horizontalColumnOffset;
                if (isCoordinateWithinGridBounds(
                    simulationGridState,
                    neighbourVerticalRowCoordinate,
                    neighbourHorizontalColumnCoordinate)
                    && simulationGridState.environmentalTerrainTypeAt(
                    neighbourVerticalRowCoordinate,
                    neighbourHorizontalColumnCoordinate) != ROCK_ENVIRONMENTAL_TERRAIN_TYPE) {
                    rockAdjacentCoordinateKeys.add(encodeCoordinateKey(
                        neighbourVerticalRowCoordinate,
                        neighbourHorizontalColumnCoordinate,
                        horizontalColumnCoordinateCount));
                }
            }
        }
    }

    private static void collectAvailableDeadNeighbourhoodCoordinateKeysAroundCell(
            SimulationGridState simulationGridState,
            int centreVerticalRowCoordinate,
            int centreHorizontalColumnCoordinate,
            FrontierCoordinateSet frontierCoordinateSet) {

        int horizontalColumnCoordinateCount = simulationGridState.horizontalColumnCoordinateCount();
        for (int verticalRowOffset = -1; verticalRowOffset <= 1; verticalRowOffset++) {
            for (int horizontalColumnOffset = -1; horizontalColumnOffset <= 1; horizontalColumnOffset++) {
                if (Math.abs(verticalRowOffset) + Math.abs(horizontalColumnOffset) != 1) {
                    continue;
                }
                int neighbourVerticalRowCoordinate = centreVerticalRowCoordinate + verticalRowOffset;
                int neighbourHorizontalColumnCoordinate = centreHorizontalColumnCoordinate + horizontalColumnOffset;
                if (isCoordinateWithinGridBounds(
                    simulationGridState,
                    neighbourVerticalRowCoordinate,
                    neighbourHorizontalColumnCoordinate)
                    && simulationGridState.plantIndexOfCell(neighbourVerticalRowCoordinate, neighbourHorizontalColumnCoordinate)
                    == SimulationGridState.DEAD_PLANT_INDEX
                    && simulationGridState.environmentalTerrainTypeAt(
                    neighbourVerticalRowCoordinate,
                    neighbourHorizontalColumnCoordinate) != ROCK_ENVIRONMENTAL_TERRAIN_TYPE) {
                    frontierCoordinateSet.addCoordinateKeyIfAbsent(encodeCoordinateKey(
                        neighbourVerticalRowCoordinate,
                        neighbourHorizontalColumnCoordinate,
                        horizontalColumnCoordinateCount));
                }
            }
        }
    }

    private static List<Coordinate> decodeCoordinateKeysIntoCoordinateList(
            SimulationGridState simulationGridState,
            Set<Integer> coordinateKeys) {

        List<Coordinate> decodedCoordinates = new ArrayList<>(coordinateKeys.size());
        int horizontalColumnCoordinateCount = simulationGridState.horizontalColumnCoordinateCount();
        for (int coordinateKey : coordinateKeys) {
            decodedCoordinates.add(new Coordinate(
                coordinateKey / horizontalColumnCoordinateCount,
                coordinateKey % horizontalColumnCoordinateCount));
        }
        return List.copyOf(decodedCoordinates);
    }

    private static int encodeCoordinateKey(
            int verticalRowCoordinate,
            int horizontalColumnCoordinate,
            int horizontalColumnCoordinateCount) {

        return verticalRowCoordinate * horizontalColumnCoordinateCount + horizontalColumnCoordinate;
    }

    private static boolean isCoordinateWithinGridBounds(
            SimulationGridState simulationGridState,
            int verticalRowCoordinate,
            int horizontalColumnCoordinate) {

        return verticalRowCoordinate >= 0
            && verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount()
            && horizontalColumnCoordinate >= 0
            && horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount();
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
                SimulationOptimizer.DEFAULT_SPREAD_PROPAGATION_RADIUS,
                SimulationOptimizer.DEFAULT_SPREAD_PROPAGATION_GEOMETRY,
                ALL_KNOWN_PLANTS_POSSESS_NO_WINTER_SPREAD_WEAKNESS,
                SimulationOptimizer.DEFAULT_TIME_REQUIRED_TO_REACH_MATURITY_IN_TICKS,
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
        return (long) finalLivingPlantCount * SimulationOptimizer.SCORE_MULTIPLIER_PER_LIVING_PLANT + finalTotalNutrientPoints;
    }

    private static void writeSolutionFile(
            File outputSolutionFile,
            LevelState levelState,
            long optimizationSeedCount,
            HeuristicSimulationRunScore bestScoringHeuristicRun) throws IOException {

        File parentOutputDirectory = outputSolutionFile.getParentFile();
        if (parentOutputDirectory != null && !parentOutputDirectory.exists()) {
            parentOutputDirectory.mkdirs();
        }

        Map<String, Object> solutionOutput = new LinkedHashMap<>();
        solutionOutput.put("level_number", 4);
        solutionOutput.put("deterministic_reproducibility", true);
        solutionOutput.put("total_simulation_ticks_advanced", levelState.tickCount());
        solutionOutput.put("optimization_seed_count_compared", optimizationSeedCount);
        solutionOutput.put("winning_optimization_seed", bestScoringHeuristicRun.winningOptimizationSeedIndex());
        solutionOutput.put("final_score", bestScoringHeuristicRun.finalScore());
        solutionOutput.put("final_living_plant_count", bestScoringHeuristicRun.finalLivingPlantCount());
        solutionOutput.put("final_total_nutrient_points", bestScoringHeuristicRun.finalTotalNutrientPoints());
        solutionOutput.put("placement_sequence", bestScoringHeuristicRun.placementActionsRecord());
        solutionOutput.put("final_plant_population_grid", bestScoringHeuristicRun.finalPlantPopulationGrid());
        solutionOutput.put("final_cellular_nutrient_grid", bestScoringHeuristicRun.finalCellularNutrientGrid());

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

    public record Coordinate(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        public Coordinate {
            if (verticalRowCoordinate < 0) {
                throw new IllegalArgumentException(
                    "Vertical row coordinate cannot be negative; received [" + verticalRowCoordinate + "].");
            }
            if (horizontalColumnCoordinate < 0) {
                throw new IllegalArgumentException(
                    "Horizontal column coordinate cannot be negative; received [" + horizontalColumnCoordinate + "].");
            }
        }
    }

    static final class FrontierCoordinateSet {
        private final List<Integer> frontierCoordinateKeyList = new ArrayList<>();
        private final Map<Integer, Integer> frontierCoordinateKeyToIndexMap = new HashMap<>();

        void addCoordinateKeyIfAbsent(int coordinateKey) {
            if (frontierCoordinateKeyToIndexMap.containsKey(coordinateKey)) {
                return;
            }
            frontierCoordinateKeyToIndexMap.put(coordinateKey, frontierCoordinateKeyList.size());
            frontierCoordinateKeyList.add(coordinateKey);
        }

        Coordinate selectRandomAvailableCoordinate(SimulationGridState simulationGridState, Random deterministicPlacementRandom) {
            if (frontierCoordinateKeyList.isEmpty()) {
                return null;
            }
            int startingCoordinateIndex = deterministicPlacementRandom.nextInt(frontierCoordinateKeyList.size());
            for (int candidateOffset = 0; candidateOffset < frontierCoordinateKeyList.size(); candidateOffset++) {
                int candidateListIndex = (startingCoordinateIndex + candidateOffset) % frontierCoordinateKeyList.size();
                int candidateCoordinateKey = frontierCoordinateKeyList.get(candidateListIndex);
                int horizontalColumnCoordinateCount = simulationGridState.horizontalColumnCoordinateCount();
                Coordinate candidateCoordinate = new Coordinate(
                    candidateCoordinateKey / horizontalColumnCoordinateCount,
                    candidateCoordinateKey % horizontalColumnCoordinateCount);
                if (simulationGridState.plantIndexOfCell(
                    candidateCoordinate.verticalRowCoordinate(),
                    candidateCoordinate.horizontalColumnCoordinate()) == SimulationGridState.DEAD_PLANT_INDEX) {
                    return candidateCoordinate;
                }
            }
            return null;
        }
    }

    record HeuristicSimulationRunScore(
            long winningOptimizationSeedIndex,
            long finalScore,
            int finalLivingPlantCount,
            long finalTotalNutrientPoints,
            List<SimulationOptimizer.PlantingAction> placementActionsRecord,
            int[][] finalPlantPopulationGrid,
            int[][] finalCellularNutrientGrid,
            boolean wasFastFailedSimulation) {
    }
}