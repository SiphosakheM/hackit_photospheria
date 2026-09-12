package com.photospheria.engine.level1.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LevelStateParsingTest {

    private static final String LEVEL_ONE_STATE_RESOURCE_NAME = "/level_one_state.json";

    private ObjectMapper objectMapper;

    @BeforeEach
    void initialiseJacksonObjectMapper() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void animalsAreEnabledFalseInTheLevelConfiguration() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        assertFalse(parsedLevelState.animalsEnabled());
    }

    @Test
    void levelGridDimensionsMapToExactlyFiftyRowsByFiftyColumns() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        assertEquals(50, parsedLevelState.rowCount());
        assertEquals(50, parsedLevelState.columnCount());
    }

    @Test
    void cellsArrayParsesIntoCellConfigurationObjects() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        List<CellConfiguration> parsedCellConfigurations = parsedLevelState.cellConfigurations();
        assertNotNull(parsedCellConfigurations);
        assertFalse(parsedCellConfigurations.isEmpty());
    }

    @Test
    void firstCellConfigurationPreservesRowColumnTerrainAndSoil() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        CellConfiguration firstCellConfiguration = parsedLevelState.cellConfigurations().getFirst();
        assertEquals(0, firstCellConfiguration.row());
        assertEquals(10, firstCellConfiguration.column());
        assertEquals(2, firstCellConfiguration.terrain());
        assertEquals(0, firstCellConfiguration.soil());
    }

    @Test
    void commandsArrayParsesIntoSimulationCommandObjects() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        List<SimulationCommand> parsedSimulationCommands = parsedLevelState.simulationCommands();
        assertNotNull(parsedSimulationCommands);
        assertEquals(4, parsedSimulationCommands.size());
    }

    @Test
    void firstSimulationCommandPreservesTypeTickAndSeason() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        SimulationCommand firstSimulationCommand = parsedLevelState.simulationCommands().getFirst();
        assertEquals("season", firstSimulationCommand.commandType());
        assertEquals(100, firstSimulationCommand.executionTick());
        assertTrue(firstSimulationCommand.seasonNamePresent().isPresent());
        assertEquals("Summer", firstSimulationCommand.seasonNamePresent().orElseThrow());
    }

    @Test
    void simulationCommandsProgressThroughAllFourSeasons() throws IOException {
        LevelState parsedLevelState = parseLevelStateFromResource();
        List<SimulationCommand> parsedSimulationCommands = parsedLevelState.simulationCommands();
        assertEquals(
            List.of("Summer", "Autumn", "Winter", "Spring"),
            parsedSimulationCommands.stream().map(
                    simulationCommand -> simulationCommand.seasonNamePresent().orElseThrow())
                .toList());
    }

    private LevelState parseLevelStateFromResource() throws IOException {
        String levelOneStateJson = loadLevelOneStateAsString();
        return objectMapper.readValue(levelOneStateJson, LevelState.class);
    }

    private String loadLevelOneStateAsString() throws IOException {
        try (InputStream resourceInputStream = LevelStateParsingTest.class.getResourceAsStream(LEVEL_ONE_STATE_RESOURCE_NAME)) {
            assertNotNull(resourceInputStream, "Level state resource must be present on the classpath.");
            return new String(resourceInputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}