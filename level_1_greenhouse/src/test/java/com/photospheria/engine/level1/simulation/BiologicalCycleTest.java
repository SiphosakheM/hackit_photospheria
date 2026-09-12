package com.photospheria.engine.level1.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.photospheria.engine.level1.configuration.LevelState;
import java.util.List;
import org.junit.jupiter.api.Test;

class BiologicalCycleTest {

    private static final int TEST_VERTICAL_ROW_COORDINATE = 10;
    private static final int TEST_HORIZONTAL_COLUMN_COORDINATE = 10;
    private static final int ACTIVE_PLANT_INDEX = 1;

    @Test
    void activePlantDrainsExactlyOneNutrientPointPerTick() {
        SimulationGridState simulationGridState = createSimulationGridStateWithPlantedCell();
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        assertEquals(100, simulationGridState.cellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertEquals(99, simulationGridState.cellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertEquals(98, simulationGridState.cellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
    }

    @Test
    void plantDiesAndPlantIndexResetsToZeroWhenNutrientCapacityReachesZero() {
        SimulationGridState simulationGridState = createSimulationGridStateWithPlantedCell();
        simulationGridState.setCellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, 1);
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();

        assertEquals(0, simulationGridState.cellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
        assertEquals(0, simulationGridState.plantIndexOfCell(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
        assertEquals(0, simulationGridState.plantAgeInTicksAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
    }

    @Test
    void deadMatterCellRegeneratesOneNutrientPointPerTick() {
        SimulationGridState simulationGridState = createSimulationGridState();
        simulationGridState.setCellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, 42);
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertEquals(43, simulationGridState.cellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertEquals(44, simulationGridState.cellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
    }

    @Test
    void deadMatterCellRegenerationStopsAtTheStrictMaximumOfOneHundredNutrientPoints() {
        SimulationGridState simulationGridState = createSimulationGridState();
        simulationGridState.setCellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, 99);
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertEquals(100, simulationGridState.cellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertEquals(100, simulationGridState.cellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
    }

    @Test
    void newlyRegisteredPlantTracksZeroTicksAlive() {
        SimulationGridState simulationGridState = createSimulationGridStateWithPlantedCell();
        assertEquals(0, simulationGridState.plantAgeInTicksAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
    }

    @Test
    void survivingPlantIncrementsTicksAliveByOneEachTick() {
        SimulationGridState simulationGridState = createSimulationGridStateWithPlantedCell();
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertEquals(1, simulationGridState.plantAgeInTicksAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertEquals(2, simulationGridState.plantAgeInTicksAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
    }

    @Test
    void plantCannotExecuteSpreadActionUntilTicksAliveReachesMaturityThreshold() {
        final int timeRequiredToReachMaturityInTicks = 5;
        SimulationGridState simulationGridState = createSimulationGridStateWithPlantedCell();
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        assertFalse(
            biologicalLifecycleProcessor.canPlantExecuteSpreadActionAt(
                TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, timeRequiredToReachMaturityInTicks));

        for (int tickCounterSoFar = 1; tickCounterSoFar < timeRequiredToReachMaturityInTicks; tickCounterSoFar++) {
            biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
            assertFalse(
                biologicalLifecycleProcessor.canPlantExecuteSpreadActionAt(
                    TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, timeRequiredToReachMaturityInTicks),
                "Plant with ["
                    + tickCounterSoFar
                    + "] ticks alive must not yet execute a spread action against a maturity threshold of ["
                    + timeRequiredToReachMaturityInTicks
                    + "] ticks.");
        }

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();
        assertTrue(
            biologicalLifecycleProcessor.canPlantExecuteSpreadActionAt(
                TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, timeRequiredToReachMaturityInTicks));
        assertEquals(timeRequiredToReachMaturityInTicks, simulationGridState.plantAgeInTicksAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
    }

    @Test
    void deadPlantResetsTicksAliveToZeroAndCannotSpread() {
        SimulationGridState simulationGridState = createSimulationGridStateWithPlantedCell();
        simulationGridState.setCellularNutrientPointsAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, 1);
        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);

        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();

        assertEquals(0, simulationGridState.plantAgeInTicksAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE));
        assertFalse(
            biologicalLifecycleProcessor.canPlantExecuteSpreadActionAt(
                TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, 1));
    }

    private SimulationGridState createSimulationGridStateWithPlantedCell() {
        SimulationGridState simulationGridState = createSimulationGridState();
        simulationGridState.registerNewPlantAt(TEST_VERTICAL_ROW_COORDINATE, TEST_HORIZONTAL_COLUMN_COORDINATE, ACTIVE_PLANT_INDEX);
        return simulationGridState;
    }

    private SimulationGridState createSimulationGridState() {
        LevelState minimalLevelState = new LevelState(false, 50, 50, 500, List.of(), List.of());
        return new SimulationGridState(minimalLevelState);
    }
}