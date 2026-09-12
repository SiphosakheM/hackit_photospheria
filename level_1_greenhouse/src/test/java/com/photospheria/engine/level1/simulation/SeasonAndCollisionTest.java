package com.photospheria.engine.level1.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.photospheria.engine.level1.configuration.LevelState;
import com.photospheria.engine.level1.configuration.SimulationCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeasonAndCollisionTest {

    private static final int GRID_VERTICAL_ROW_COUNT = 50;
    private static final int GRID_HORIZONTAL_COLUMN_COUNT = 50;

    @Test
    void engineAppliesSeasonCommandsAtTicksOneHundredTwoHundredThreeHundredAndFourHundred() {
        SimulationTickEngine simulationTickEngine = createEngineWithSeasonCommands();

        assertEquals("Spring", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(50);
        assertEquals("Spring", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(100);
        assertEquals("Summer", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(199);
        assertEquals("Summer", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(200);
        assertEquals("Autumn", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(299);
        assertEquals("Autumn", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(300);
        assertEquals("Winter", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(399);
        assertEquals("Winter", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(400);
        assertEquals("Spring", simulationTickEngine.currentEnvironmentalSeason());

        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(500);
        assertEquals("Spring", simulationTickEngine.currentEnvironmentalSeason());
    }

    @Test
    void plantWithNoWinterSpreadWeaknessReturnsEmptySpreadCoordinatesDuringWinterSeason() {
        SimulationTickEngine simulationTickEngine = createEngineWithSeasonCommands();
        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(300);

        List<SimulationTickEngine.SimulationCoordinate> spreadTargetCoordinates = simulationTickEngine.resolveSeasonalSpreadTargetCoordinates(
            25, 25, 1, "Row", true, GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertTrue(
            spreadTargetCoordinates.isEmpty(),
            "No-winter-spread plants must not resolve any spread coordinates during the Winter season.");
    }

    @Test
    void plantWithoutNoWinterSpreadWeaknessSpreadsNormallyDuringWinterSeason() {
        SimulationTickEngine simulationTickEngine = createEngineWithSeasonCommands();
        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(300);

        List<SimulationTickEngine.SimulationCoordinate> spreadTargetCoordinates = simulationTickEngine.resolveSeasonalSpreadTargetCoordinates(
            25, 25, 1, "Row", false, GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(25, 24),
                coordinateAt(25, 26)),
            spreadTargetCoordinates);
    }

    @Test
    void plantWithNoWinterSpreadWeaknessSpreadsNormallyOutsideWinterSeason() {
        SimulationTickEngine simulationTickEngine = createEngineWithSeasonCommands();
        simulationTickEngine.updateEnvironmentalSeasonForCurrentTick(200);

        List<SimulationTickEngine.SimulationCoordinate> spreadTargetCoordinates = simulationTickEngine.resolveSeasonalSpreadTargetCoordinates(
            25, 25, 1, "Row", true, GRID_VERTICAL_ROW_COUNT, GRID_HORIZONTAL_COLUMN_COUNT);

        assertEquals(
            List.of(
                coordinateAt(25, 24),
                coordinateAt(25, 26)),
            spreadTargetCoordinates);
    }

    @Test
    void collidingMaturePlantSpreadsResolveDeterministicallyToTheLastExecutingPlant() {
        LevelState minimalLevelState = new LevelState(false, 50, 50, 500, List.of(), List.of());
        SimulationGridState simulationGridState = new SimulationGridState(minimalLevelState);
        SimulationTickEngine simulationTickEngine = new SimulationTickEngine(simulationGridState, List.of());

        simulationGridState.registerNewPlantAt(10, 10, 1);
        simulationGridState.registerNewPlantAt(12, 10, 2);

        BiologicalLifecycleProcessor biologicalLifecycleProcessor = new BiologicalLifecycleProcessor(simulationGridState);
        biologicalLifecycleProcessor.advanceBiologicalProcessesForSingleTick();

        simulationTickEngine.executeSpreadForSinglePlant(10, 10, 1, "VonNeumann", false, 1, 1);
        simulationTickEngine.executeSpreadForSinglePlant(12, 10, 1, "VonNeumann", false, 1, 2);

        assertEquals(2, simulationGridState.plantIndexOfCell(11, 10));
        assertEquals(1, simulationGridState.plantIndexOfCell(9, 10));
        assertEquals(2, simulationGridState.plantIndexOfCell(13, 10));
    }

    @Test
    void immaturePlantDoesNotExecuteAnySpreadAction() {
        LevelState minimalLevelState = new LevelState(false, 50, 50, 500, List.of(), List.of());
        SimulationGridState simulationGridState = new SimulationGridState(minimalLevelState);
        SimulationTickEngine simulationTickEngine = new SimulationTickEngine(simulationGridState, List.of());

        simulationGridState.registerNewPlantAt(10, 10, 1);

        simulationTickEngine.executeSpreadForSinglePlant(10, 10, 1, "VonNeumann", false, 2, 1);

        assertEquals(SimulationGridState.DEAD_PLANT_INDEX, simulationGridState.plantIndexOfCell(9, 10));
        assertEquals(SimulationGridState.DEAD_PLANT_INDEX, simulationGridState.plantIndexOfCell(10, 9));
        assertEquals(SimulationGridState.DEAD_PLANT_INDEX, simulationGridState.plantIndexOfCell(10, 11));
        assertEquals(SimulationGridState.DEAD_PLANT_INDEX, simulationGridState.plantIndexOfCell(11, 10));
    }

    @Test
    void deadSourceCellDoesNotExecuteAnySpreadAction() {
        LevelState minimalLevelState = new LevelState(false, 50, 50, 500, List.of(), List.of());
        SimulationGridState simulationGridState = new SimulationGridState(minimalLevelState);
        SimulationTickEngine simulationTickEngine = new SimulationTickEngine(simulationGridState, List.of());

        simulationTickEngine.executeSpreadForSinglePlant(10, 10, 1, "VonNeumann", false, 0, 1);

        assertEquals(SimulationGridState.DEAD_PLANT_INDEX, simulationGridState.plantIndexOfCell(11, 10));
    }

    private SimulationTickEngine createEngineWithSeasonCommands() {
        List<SimulationCommand> seasonalSimulationCommands = List.of(
            new SimulationCommand("season", 100, "Summer"),
            new SimulationCommand("season", 200, "Autumn"),
            new SimulationCommand("season", 300, "Winter"),
            new SimulationCommand("season", 400, "Spring"));
        LevelState levelState = new LevelState(false, 50, 50, 500, List.of(), seasonalSimulationCommands);
        return new SimulationTickEngine(levelState);
    }

    private SimulationTickEngine.SimulationCoordinate coordinateAt(int targetVerticalRowCoordinate, int targetHorizontalColumnCoordinate) {
        return new SimulationTickEngine.SimulationCoordinate(targetVerticalRowCoordinate, targetHorizontalColumnCoordinate);
    }
}