package com.photospheria.engine.level1.simulation;

import com.photospheria.engine.level1.configuration.LevelState;
import com.photospheria.engine.level1.configuration.SimulationCommand;
import java.util.ArrayList;
import java.util.List;

public class SimulationTickEngine {

    private static final String DEFAULT_ENVIRONMENTAL_SEASON = "Spring";
    private static final String WINTER_ENVIRONMENTAL_SEASON = "Winter";
    private static final String SEASON_COMMAND_TYPE = "season";
    private static final String NO_WINTER_SPREAD_WEAKNESS_RULE = "no_winter_spread";

    private static final String VON_NEUMANN_SPATIAL_PROPAGATION_GEOMETRY = "VonNeumann";
    private static final String MOORE_SPATIAL_PROPAGATION_GEOMETRY = "Moore";
    private static final String ROW_SPATIAL_PROPAGATION_GEOMETRY = "Row";
    private static final String COLUMN_SPATIAL_PROPAGATION_GEOMETRY = "Column";
    private static final String CROSS_HATCH_SPATIAL_PROPAGATION_GEOMETRY = "CrossHatch";

    private String currentEnvironmentalSeason;
    private final SimulationGridState simulationGridState;
    private final List<SimulationCommand> simulationCommands;

    public record SimulationCoordinate(
            int targetVerticalRowCoordinate,
            int targetHorizontalColumnCoordinate) {
    }

    public SimulationTickEngine() {
        this.currentEnvironmentalSeason = DEFAULT_ENVIRONMENTAL_SEASON;
        this.simulationGridState = null;
        this.simulationCommands = List.of();
    }

    public SimulationTickEngine(LevelState levelState) {
        this(
            new SimulationGridState(levelState),
            levelState.simulationCommands());
    }

    public SimulationTickEngine(SimulationGridState simulationGridState, List<SimulationCommand> simulationCommands) {
        if (simulationGridState == null) {
            throw new IllegalArgumentException("SimulationGridState cannot be null.");
        }
        if (simulationCommands == null) {
            throw new IllegalArgumentException("SimulationCommands list cannot be null.");
        }
        this.currentEnvironmentalSeason = DEFAULT_ENVIRONMENTAL_SEASON;
        this.simulationGridState = simulationGridState;
        this.simulationCommands = List.copyOf(simulationCommands);
    }

    public SimulationGridState simulationGridState() {
        return simulationGridState;
    }

    public String currentEnvironmentalSeason() {
        return currentEnvironmentalSeason;
    }

    public void updateEnvironmentalSeasonForCurrentTick(int currentSimulationTick) {
        if (currentSimulationTick < 0) {
            throw new IllegalArgumentException(
                "Current simulation tick cannot be negative; received [" + currentSimulationTick + "].");
        }
        String latestApplicableSeasonName = DEFAULT_ENVIRONMENTAL_SEASON;
        for (SimulationCommand simulationCommand : simulationCommands) {
            if (SEASON_COMMAND_TYPE.equalsIgnoreCase(simulationCommand.commandType())
                && simulationCommand.executionTick() <= currentSimulationTick) {
                latestApplicableSeasonName = simulationCommand.seasonNamePresent().orElse(latestApplicableSeasonName);
            }
        }
        this.currentEnvironmentalSeason = latestApplicableSeasonName;
    }

    public List<SimulationCoordinate> resolveSeasonalSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            String spatialPropagationGeometryType,
            boolean doesPlantPossessNoWinterSpreadWeakness,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        if (doesPlantPossessNoWinterSpreadWeakness
            && WINTER_ENVIRONMENTAL_SEASON.equalsIgnoreCase(currentEnvironmentalSeason)) {
            return List.of();
        }

        return resolveSpreadTargetCoordinates(
            centerVerticalRowCoordinate,
            centerHorizontalColumnCoordinate,
            propagationSpreadRadius,
            spatialPropagationGeometryType,
            gridVerticalRowCount,
            gridHorizontalColumnCount);
    }

    public void executeSpreadForSinglePlant(
            int sourceVerticalRowCoordinate,
            int sourceHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            String spatialPropagationGeometryType,
            boolean doesPlantPossessNoWinterSpreadWeakness,
            int timeRequiredToReachMaturityInTicks,
            int plantIndexToPlantWhenSpreadSucceeds) {

        requireSimulationGridStateAvailable();
        if (simulationGridState.plantIndexOfCell(sourceVerticalRowCoordinate, sourceHorizontalColumnCoordinate)
            == SimulationGridState.DEAD_PLANT_INDEX) {
            return;
        }
        if (simulationGridState.plantAgeInTicksAt(sourceVerticalRowCoordinate, sourceHorizontalColumnCoordinate)
            < timeRequiredToReachMaturityInTicks) {
            return;
        }

        List<SimulationCoordinate> spreadTargetCoordinates = resolveSeasonalSpreadTargetCoordinates(
            sourceVerticalRowCoordinate,
            sourceHorizontalColumnCoordinate,
            propagationSpreadRadius,
            spatialPropagationGeometryType,
            doesPlantPossessNoWinterSpreadWeakness,
            simulationGridState.verticalRowCoordinateCount(),
            simulationGridState.horizontalColumnCoordinateCount());

        for (SimulationCoordinate spreadTargetCoordinate : spreadTargetCoordinates) {
            simulationGridState.registerNewPlantAt(
                spreadTargetCoordinate.targetVerticalRowCoordinate(),
                spreadTargetCoordinate.targetHorizontalColumnCoordinate(),
                plantIndexToPlantWhenSpreadSucceeds);
        }
    }

    private void requireSimulationGridStateAvailable() {
        if (simulationGridState == null) {
            throw new IllegalStateException(
                "SimulationGridState is required to execute spread actions; construct this engine with a SimulationGridState or LevelState.");
        }
    }

    public List<SimulationCoordinate> resolveSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            String spatialPropagationGeometryType,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        validateSpreadEngineArguments(
            centerVerticalRowCoordinate,
            centerHorizontalColumnCoordinate,
            propagationSpreadRadius,
            gridVerticalRowCount,
            gridHorizontalColumnCount);

        return switch (spatialPropagationGeometryType) {
            case String geometryType when geometryType.equalsIgnoreCase(VON_NEUMANN_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveVonNeumannSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            case String geometryType when geometryType.equalsIgnoreCase(MOORE_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveMooreSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            case String geometryType when geometryType.equalsIgnoreCase(ROW_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveRowSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            case String geometryType when geometryType.equalsIgnoreCase(COLUMN_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveColumnSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            case String geometryType when geometryType.equalsIgnoreCase(CROSS_HATCH_SPATIAL_PROPAGATION_GEOMETRY) ->
                resolveCrossHatchSpreadTargetCoordinates(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate,
                    propagationSpreadRadius,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount);
            default ->
                throw new IllegalArgumentException(
                    "Unsupported spatial propagation geometry type ["
                        + spatialPropagationGeometryType
                        + "]; supported geometries are: VonNeumann, Moore, Row, Column, CrossHatch.");
        };
    }

    private List<SimulationCoordinate> resolveVonNeumannSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int verticalRowOffset = -propagationSpreadRadius; verticalRowOffset <= propagationSpreadRadius; verticalRowOffset++) {
            for (int horizontalColumnOffset = -propagationSpreadRadius; horizontalColumnOffset <= propagationSpreadRadius; horizontalColumnOffset++) {
                if (isWithinVonNeumannManhattanDistance(verticalRowOffset, horizontalColumnOffset, propagationSpreadRadius)
                    && isNotTheCenterCoordinate(verticalRowOffset, horizontalColumnOffset)) {
                    addInBoundsTargetCoordinate(
                        centerVerticalRowCoordinate + verticalRowOffset,
                        centerHorizontalColumnCoordinate + horizontalColumnOffset,
                        gridVerticalRowCount,
                        gridHorizontalColumnCount,
                        listOfValidTargetCoordinates);
                }
            }
        }
        return listOfValidTargetCoordinates;
    }

    private List<SimulationCoordinate> resolveMooreSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int verticalRowOffset = -propagationSpreadRadius; verticalRowOffset <= propagationSpreadRadius; verticalRowOffset++) {
            for (int horizontalColumnOffset = -propagationSpreadRadius; horizontalColumnOffset <= propagationSpreadRadius; horizontalColumnOffset++) {
                if (isWithinMooreChebyshevDistance(verticalRowOffset, horizontalColumnOffset, propagationSpreadRadius)
                    && isNotTheCenterCoordinate(verticalRowOffset, horizontalColumnOffset)) {
                    addInBoundsTargetCoordinate(
                        centerVerticalRowCoordinate + verticalRowOffset,
                        centerHorizontalColumnCoordinate + horizontalColumnOffset,
                        gridVerticalRowCount,
                        gridHorizontalColumnCount,
                        listOfValidTargetCoordinates);
                }
            }
        }
        return listOfValidTargetCoordinates;
    }

    private List<SimulationCoordinate> resolveRowSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int horizontalColumnOffset = -propagationSpreadRadius; horizontalColumnOffset <= propagationSpreadRadius; horizontalColumnOffset++) {
            if (horizontalColumnOffset != 0) {
                addInBoundsTargetCoordinate(
                    centerVerticalRowCoordinate,
                    centerHorizontalColumnCoordinate + horizontalColumnOffset,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount,
                    listOfValidTargetCoordinates);
            }
        }
        return listOfValidTargetCoordinates;
    }

    private List<SimulationCoordinate> resolveColumnSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int verticalRowOffset = -propagationSpreadRadius; verticalRowOffset <= propagationSpreadRadius; verticalRowOffset++) {
            if (verticalRowOffset != 0) {
                addInBoundsTargetCoordinate(
                    centerVerticalRowCoordinate + verticalRowOffset,
                    centerHorizontalColumnCoordinate,
                    gridVerticalRowCount,
                    gridHorizontalColumnCount,
                    listOfValidTargetCoordinates);
            }
        }
        return listOfValidTargetCoordinates;
    }

    private List<SimulationCoordinate> resolveCrossHatchSpreadTargetCoordinates(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        List<SimulationCoordinate> listOfValidTargetCoordinates = new ArrayList<>();
        for (int verticalRowOffset = -propagationSpreadRadius; verticalRowOffset <= propagationSpreadRadius; verticalRowOffset++) {
            for (int horizontalColumnOffset = -propagationSpreadRadius; horizontalColumnOffset <= propagationSpreadRadius; horizontalColumnOffset++) {
                if (isDiagonalOffsetWithinCrossHatch(verticalRowOffset, horizontalColumnOffset)) {
                    addInBoundsTargetCoordinate(
                        centerVerticalRowCoordinate + verticalRowOffset,
                        centerHorizontalColumnCoordinate + horizontalColumnOffset,
                        gridVerticalRowCount,
                        gridHorizontalColumnCount,
                        listOfValidTargetCoordinates);
                }
            }
        }
        return listOfValidTargetCoordinates;
    }

    private boolean isWithinVonNeumannManhattanDistance(int verticalRowOffset, int horizontalColumnOffset, int propagationSpreadRadius) {
        return Math.abs(verticalRowOffset) + Math.abs(horizontalColumnOffset) <= propagationSpreadRadius;
    }

    private boolean isWithinMooreChebyshevDistance(int verticalRowOffset, int horizontalColumnOffset, int propagationSpreadRadius) {
        return Math.abs(verticalRowOffset) <= propagationSpreadRadius
            && Math.abs(horizontalColumnOffset) <= propagationSpreadRadius;
    }

    private boolean isDiagonalOffsetWithinCrossHatch(int verticalRowOffset, int horizontalColumnOffset) {
        return isNotTheCenterCoordinate(verticalRowOffset, horizontalColumnOffset)
            && Math.abs(verticalRowOffset) == Math.abs(horizontalColumnOffset);
    }

    private boolean isNotTheCenterCoordinate(int verticalRowOffset, int horizontalColumnOffset) {
        return verticalRowOffset != 0 || horizontalColumnOffset != 0;
    }

    private void addInBoundsTargetCoordinate(
            int targetVerticalRowCoordinate,
            int targetHorizontalColumnCoordinate,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount,
            List<SimulationCoordinate> listOfValidTargetCoordinates) {

        if (targetVerticalRowCoordinate >= 0
            && targetVerticalRowCoordinate < gridVerticalRowCount
            && targetHorizontalColumnCoordinate >= 0
            && targetHorizontalColumnCoordinate < gridHorizontalColumnCount) {
            listOfValidTargetCoordinates.add(
                new SimulationCoordinate(targetVerticalRowCoordinate, targetHorizontalColumnCoordinate));
        }
    }

    private void validateSpreadEngineArguments(
            int centerVerticalRowCoordinate,
            int centerHorizontalColumnCoordinate,
            int propagationSpreadRadius,
            int gridVerticalRowCount,
            int gridHorizontalColumnCount) {

        if (gridVerticalRowCount <= 0) {
            throw new IllegalArgumentException(
                "Grid vertical row count must be positive; received [" + gridVerticalRowCount + "].");
        }
        if (gridHorizontalColumnCount <= 0) {
            throw new IllegalArgumentException(
                "Grid horizontal column count must be positive; received [" + gridHorizontalColumnCount + "].");
        }
        if (centerVerticalRowCoordinate < 0 || centerVerticalRowCoordinate >= gridVerticalRowCount) {
            throw new IllegalArgumentException(
                "Center vertical row coordinate [" + centerVerticalRowCoordinate
                    + "] is outside the grid row bounds [0, " + (gridVerticalRowCount - 1) + "].");
        }
        if (centerHorizontalColumnCoordinate < 0 || centerHorizontalColumnCoordinate >= gridHorizontalColumnCount) {
            throw new IllegalArgumentException(
                "Center horizontal column coordinate [" + centerHorizontalColumnCoordinate
                    + "] is outside the grid column bounds [0, " + (gridHorizontalColumnCount - 1) + "].");
        }
        if (propagationSpreadRadius < 0) {
            throw new IllegalArgumentException(
                "Propagation spread radius cannot be negative; received [" + propagationSpreadRadius + "].");
        }
    }
}