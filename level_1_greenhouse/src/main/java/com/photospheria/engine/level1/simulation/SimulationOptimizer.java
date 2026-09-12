package com.photospheria.engine.level1.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photospheria.engine.level1.configuration.LevelState;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SimulationOptimizer {

    public static final long DEFAULT_OPTIMIZATION_SEED_COUNT = 32L;
    public static final int DEFAULT_STARTER_PLANT_INDEX = 1;
    public static final int STARTER_SEEDLING_CANDIDATE_PATH_COUNT = 8;
    public static final int DEFAULT_SPREAD_PROPAGATION_RADIUS = 1;
    public static final String DEFAULT_SPREAD_PROPAGATION_GEOMETRY = "VonNeumann";
    public static final int DEFAULT_TIME_REQUIRED_TO_REACH_MATURITY_IN_TICKS = 1;
    public static final boolean DEFAULT_DOES_PLANT_POSSESS_NO_WINTER_SPREAD_WEAKNESS = false;
    public static final int SCORE_MULTIPLIER_PER_LIVING_PLANT = 100;

    private static final String DEFAULT_INPUT_FILE_PATH = "shared_data/level_one_state.json";
    private static final String DEFAULT_OUTPUT_FILE_PATH = "level_1_greenhouse/outputs/solution.json";

    public static void main(String[] args) {
        String inputFilePath = resolveArgumentOrDefault(args, 0, DEFAULT_INPUT_FILE_PATH);
        String outputFilePath = resolveArgumentOrDefault(args, 1, DEFAULT_OUTPUT_FILE_PATH);
        long optimizationSeedCount = parseOptimizationSeedCount(resolveArgumentOrDefault(args, 2, Long.toString(DEFAULT_OPTIMIZATION_SEED_COUNT)));

        File inputStateFile = resolveExistingInputFile(inputFilePath);
        File outputStateFile = resolveOutputFile(outputFilePath);

        try {
            LevelState levelState = readLevelStateFromFile(inputStateFile);
            OptimizationResult optimizationResult = optimize(levelState, optimizationSeedCount, outputStateFile);

            System.out.println("SimulationOptimizer completed successfully.");
            System.out.println("  Input state file               : " + inputStateFile.getAbsolutePath());
            System.out.println("  Output solution file           : " + optimizationResult.outputSolutionFile().getAbsolutePath());
            System.out.println("  Optimization seed count        : " + optimizationSeedCount);
            System.out.println("  Winning optimization seed      : " + optimizationResult.winningOptimizationSeed());
            System.out.println("  Final deterministic score      : " + optimizationResult.finalScore());
            System.out.println("  Final living plant count       : " + optimizationResult.finalLivingPlantCount());
            System.out.println("  Final total nutrient points    : " + optimizationResult.finalTotalNutrientPoints());
        } catch (IOException ioException) {
            System.err.println("SimulationOptimizer failed: " + ioException.getMessage());
            System.exit(1);
        }
    }

    public static OptimizationResult optimize(LevelState levelState, long optimizationSeedCount, File outputSolutionFile) throws IOException {
        if (levelState == null) {
            throw new IllegalArgumentException("LevelState cannot be null.");
        }
        if (optimizationSeedCount <= 0) {
            throw new IllegalArgumentException(
                "Optimization seed count must be positive; received [" + optimizationSeedCount + "].");
        }
        if (outputSolutionFile == null) {
            throw new IllegalArgumentException("Output solution file cannot be null.");
        }

        SimulationRunScore bestScoringRun = null;
        for (long optimizationSeedIndex = 1; optimizationSeedIndex <= optimizationSeedCount; optimizationSeedIndex++) {
            SimulationRunScore candidateRunScore = simulateDeterministicRun(levelState, optimizationSeedIndex);
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
            outputSolutionFile);
    }

    private static SimulationRunScore simulateDeterministicRun(LevelState levelState, long optimizationSeedIndex) {
        SimulationGridState simulationGridState = new SimulationGridState(levelState);
        placeStarterPlantCandidatesAtDeterministicCoordinates(simulationGridState, optimizationSeedIndex);

        SimulationTickEngine simulationTickEngine = new SimulationTickEngine(levelState);
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        for (int currentSimulationTick = 1; currentSimulationTick <= levelState.tickCount(); currentSimulationTick++) {
            simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(currentSimulationTick);
            biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
            executeSpreadPhaseForCurrentTick(simulationGridState, simulationTickEngine);
        }

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
            finalPlantPopulationGrid,
            finalCellularNutrientGrid);
    }

    private static void placeStarterPlantCandidatesAtDeterministicCoordinates(SimulationGridState simulationGridState, long optimizationSeedIndex) {
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
        solutionOutput.put("level_number", 1);
        solutionOutput.put("deterministic_reproducibility", true);
        solutionOutput.put("total_simulation_ticks_advanced", levelState.tickCount());
        solutionOutput.put("optimization_seed_count_compared", optimizationSeedCount);
        solutionOutput.put("winning_optimization_seed", bestScoringRun.winningOptimizationSeedIndex());
        solutionOutput.put("final_score", bestScoringRun.finalScore());
        solutionOutput.put("final_living_plant_count", bestScoringRun.finalLivingPlantCount());
        solutionOutput.put("final_total_nutrient_points", bestScoringRun.finalTotalNutrientPoints());
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

    private static File resolveOutputFile(String outputFilePath) {
        File directCandidateFile = new File(outputFilePath);
        File parentRelativeCandidateFile = new File("..", outputFilePath);
        if (directCandidateFile.isAbsolute()) {
            return directCandidateFile;
        }
        if (directCandidateFile.getParentFile() != null && directCandidateFile.getParentFile().isDirectory()) {
            return directCandidateFile;
        }
        return parentRelativeCandidateFile;
    }

    private record SimulationRunScore(
            long winningOptimizationSeedIndex,
            long finalScore,
            int finalLivingPlantCount,
            long finalTotalNutrientPoints,
            int[][] finalPlantPopulationGrid,
            int[][] finalCellularNutrientGrid) {
    }

    public record OptimizationResult(
            long winningOptimizationSeed,
            long finalScore,
            int finalLivingPlantCount,
            long finalTotalNutrientPoints,
            File outputSolutionFile) {
    }
}