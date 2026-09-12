package com.photospheria.engine.level1.simulation;

public class BiologicalLifecycleProcessor {

    private static final int NUTRIENT_POINTS_DRAINED_PER_ACTIVE_PLANT_TICK = 1;
    private static final int NUTRIENT_POINTS_REGENERATED_PER_DEAD_CELL_TICK = 1;

    private final SimulationGridState simulationGridState;

    public BiologicalLifecycleProcessor(SimulationGridState simulationGridState) {
        if (simulationGridState == null) {
            throw new IllegalArgumentException("SimulationGridState cannot be null.");
        }
        this.simulationGridState = simulationGridState;
    }

    public void advanceBiologicalProcessesForSingleTick() {
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < simulationGridState.verticalRowCoordinateCount(); verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < simulationGridState.horizontalColumnCoordinateCount(); horizontalColumnCoordinate++) {
                processSingleCell(verticalRowCoordinate, horizontalColumnCoordinate);
            }
        }
    }

    public boolean canPlantExecuteSpreadActionAt(
            int verticalRowCoordinate,
            int horizontalColumnCoordinate,
            int timeRequiredToReachMaturityInTicks) {

        if (timeRequiredToReachMaturityInTicks < 0) {
            throw new IllegalArgumentException(
                "Time required to reach maturity cannot be negative; received [" + timeRequiredToReachMaturityInTicks + "].");
        }
        return simulationGridState.plantAgeInTicksAt(verticalRowCoordinate, horizontalColumnCoordinate)
            >= timeRequiredToReachMaturityInTicks;
    }

    private void processSingleCell(int verticalRowCoordinate, int horizontalColumnCoordinate) {
        int currentPlantIndexForCell = simulationGridState.plantIndexOfCell(verticalRowCoordinate, horizontalColumnCoordinate);
        int currentNutrientCapacityForCell = simulationGridState.cellularNutrientPointsAt(verticalRowCoordinate, horizontalColumnCoordinate);

        if (currentPlantIndexForCell != SimulationGridState.DEAD_PLANT_INDEX) {
            processActivePlantCell(verticalRowCoordinate, horizontalColumnCoordinate, currentNutrientCapacityForCell);
        } else {
            processDeadMatterCell(verticalRowCoordinate, horizontalColumnCoordinate, currentNutrientCapacityForCell);
        }
    }

    private void processActivePlantCell(int verticalRowCoordinate, int horizontalColumnCoordinate, int currentNutrientCapacityForCell) {
        int reducedNutrientCapacityForCell = Math.max(
            SimulationGridState.DEAD_PLANT_INDEX,
            currentNutrientCapacityForCell - NUTRIENT_POINTS_DRAINED_PER_ACTIVE_PLANT_TICK);
        simulationGridState.setCellularNutrientPointsAt(verticalRowCoordinate, horizontalColumnCoordinate, reducedNutrientCapacityForCell);

        if (reducedNutrientCapacityForCell == 0) {
            simulationGridState.terminatePlantAt(verticalRowCoordinate, horizontalColumnCoordinate);
        } else {
            simulationGridState.incrementPlantAgeInTicksAt(verticalRowCoordinate, horizontalColumnCoordinate);
        }
    }

    private void processDeadMatterCell(int verticalRowCoordinate, int horizontalColumnCoordinate, int currentNutrientCapacityForCell) {
        int regeneratedNutrientCapacityForCell = Math.min(
            SimulationGridState.MAXIMUM_CELLULAR_NUTRIENT_POINTS_PER_CELL,
            currentNutrientCapacityForCell + NUTRIENT_POINTS_REGENERATED_PER_DEAD_CELL_TICK);
        simulationGridState.setCellularNutrientPointsAt(verticalRowCoordinate, horizontalColumnCoordinate, regeneratedNutrientCapacityForCell);
    }
}