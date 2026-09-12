package com.photospheria.engine.level2.ecosystem;

import com.photospheria.engine.level1.simulation.SimulationGridState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EcosystemValidator {

    public static final int DEFAULT_LEVEL_TWO_VERTICAL_ROW_COORDINATE_COUNT = 70;
    public static final int DEFAULT_LEVEL_TWO_HORIZONTAL_COLUMN_COORDINATE_COUNT = 100;
    public static final int UNRESOLVED_PLANT_SPECIES_INDEX = -1;

    public static final Map<String, Integer> DEFAULT_LEVEL_TWO_PLANT_SPECIES_NAME_TO_INDEX_MAPPING =
        createDefaultLevelTwoPlantSpeciesNameToIndexMapping();

    private final List<AnimalSpecies> parsedAnimalSpecies;
    private final List<PlantUnlockRule> parsedPlantUnlockRules;
    private final Map<String, Integer> plantSpeciesNameToIndexMapping;
    private final int ecosystemStatisticsArrayLength;

    private final Set<String> currentlyActiveAnimalSpecies = new LinkedHashSet<>();
    private final Set<String> currentlyActiveWeatherEvents = new LinkedHashSet<>();
    private final Map<String, Double> currentFeatureMeasurementByName = new LinkedHashMap<>();

    private int[] currentPlantSpeciesCellCountByIndex;
    private double[] currentPlantSpeciesCoverageFractionByIndex;
    private int currentTotalGridCellCount;

    public EcosystemValidator(List<AnimalSpecies> parsedAnimalSpecies, List<PlantUnlockRule> parsedPlantUnlockRules) {
        this(parsedAnimalSpecies, parsedPlantUnlockRules, DEFAULT_LEVEL_TWO_PLANT_SPECIES_NAME_TO_INDEX_MAPPING);
    }

    public EcosystemValidator(
            List<AnimalSpecies> parsedAnimalSpecies,
            List<PlantUnlockRule> parsedPlantUnlockRules,
            Map<String, Integer> plantSpeciesNameToIndexMapping) {

        if (parsedAnimalSpecies == null) {
            throw new IllegalArgumentException("Parsed animal species list cannot be null.");
        }
        if (parsedPlantUnlockRules == null) {
            throw new IllegalArgumentException("Parsed plant unlock rule list cannot be null.");
        }
        if (plantSpeciesNameToIndexMapping == null) {
            throw new IllegalArgumentException("Plant species name to index mapping cannot be null.");
        }

        this.parsedAnimalSpecies = List.copyOf(parsedAnimalSpecies);
        this.parsedPlantUnlockRules = List.copyOf(parsedPlantUnlockRules);
        this.plantSpeciesNameToIndexMapping = copyMappingIntoPreservedOrder(plantSpeciesNameToIndexMapping);

        validatePlantSpeciesNameToIndexMapping();
        validateEveryPlantUnlockRulePlantNameResolvesThroughTheMapping();

        int maximumMappedPlantSpeciesIndex = calculateMaximumMappedPlantSpeciesIndex();
        this.ecosystemStatisticsArrayLength = maximumMappedPlantSpeciesIndex + 1;
        this.currentPlantSpeciesCellCountByIndex = new int[ecosystemStatisticsArrayLength];
        this.currentPlantSpeciesCoverageFractionByIndex = new double[ecosystemStatisticsArrayLength];
    }

    public void calculateCurrentEcosystemStatistics(SimulationGridState simulationGridState) {
        if (simulationGridState == null) {
            throw new IllegalArgumentException("Simulation grid state cannot be null.");
        }

        short[][] plantPopulationGrid = simulationGridState.plantPopulationGrid();
        int verticalRowCoordinateCount = simulationGridState.verticalRowCoordinateCount();
        int horizontalColumnCoordinateCount = simulationGridState.horizontalColumnCoordinateCount();

        int[] freshlyTalliedPlantSpeciesCellCountByIndex = new int[ecosystemStatisticsArrayLength];
        for (int verticalRowCoordinate = 0; verticalRowCoordinate < verticalRowCoordinateCount; verticalRowCoordinate++) {
            for (int horizontalColumnCoordinate = 0; horizontalColumnCoordinate < horizontalColumnCoordinateCount; horizontalColumnCoordinate++) {
                int plantSpeciesIndex = Short.toUnsignedInt(plantPopulationGrid[verticalRowCoordinate][horizontalColumnCoordinate]);
                if (plantSpeciesIndex > SimulationGridState.DEAD_PLANT_INDEX && plantSpeciesIndex < ecosystemStatisticsArrayLength) {
                    freshlyTalliedPlantSpeciesCellCountByIndex[plantSpeciesIndex]++;
                }
            }
        }

        int totalGridCellCount = verticalRowCoordinateCount * horizontalColumnCoordinateCount;
        double totalGridCellCountAsDecimal = totalGridCellCount;

        double[] freshlyCalculatedPlantSpeciesCoverageFractionByIndex = new double[ecosystemStatisticsArrayLength];
        for (int plantSpeciesIndex = 0; plantSpeciesIndex < ecosystemStatisticsArrayLength; plantSpeciesIndex++) {
            freshlyCalculatedPlantSpeciesCoverageFractionByIndex[plantSpeciesIndex] =
                freshlyTalliedPlantSpeciesCellCountByIndex[plantSpeciesIndex] / totalGridCellCountAsDecimal;
        }

        this.currentPlantSpeciesCellCountByIndex = freshlyTalliedPlantSpeciesCellCountByIndex;
        this.currentPlantSpeciesCoverageFractionByIndex = freshlyCalculatedPlantSpeciesCoverageFractionByIndex;
        this.currentTotalGridCellCount = totalGridCellCount;
    }

    public Set<String> evaluateActiveAnimals() {
        Set<String> freshlyEvaluatedActiveAnimalSpecies = new LinkedHashSet<>();
        for (AnimalSpecies animalSpecies : parsedAnimalSpecies) {
            RequirementGroup speciesRequirementGroup = animalSpecies.speciesRequirementGroup();
            if (evaluateRequirementGroup(speciesRequirementGroup)) {
                freshlyEvaluatedActiveAnimalSpecies.add(animalSpecies.animalDisplayName());
            }
        }
        currentlyActiveAnimalSpecies.clear();
        currentlyActiveAnimalSpecies.addAll(freshlyEvaluatedActiveAnimalSpecies);
        return Collections.unmodifiableSet(new LinkedHashSet<>(currentlyActiveAnimalSpecies));
    }

    public List<Integer> getCurrentlyUnlockedPlants() {
        List<Integer> currentlyUnlockedPlantIndexes = new ArrayList<>();
        for (PlantUnlockRule plantUnlockRule : parsedPlantUnlockRules) {
            if (evaluateUnlockConditionNode(plantUnlockRule.unlockConditionNode())) {
                int unlockedPlantSpeciesIndex = resolvePlantIndexForPlantSpeciesNameOrThrow(plantUnlockRule.plantName());
                currentlyUnlockedPlantIndexes.add(unlockedPlantSpeciesIndex);
            }
        }
        return List.copyOf(currentlyUnlockedPlantIndexes);
    }

    public void markWeatherEventActive(String weatherEventName) {
        if (weatherEventName == null || weatherEventName.isBlank()) {
            throw new IllegalArgumentException("Weather event name must be non-blank.");
        }
        currentlyActiveWeatherEvents.add(weatherEventName);
    }

    public void clearWeatherEventActive(String weatherEventName) {
        if (weatherEventName == null || weatherEventName.isBlank()) {
            throw new IllegalArgumentException("Weather event name must be non-blank.");
        }
        currentlyActiveWeatherEvents.remove(weatherEventName);
    }

    public Set<String> currentlyActiveWeatherEvents() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(currentlyActiveWeatherEvents));
    }

    public void updateCurrentFeatureMeasurement(String featureName, double measuredFeatureValue) {
        if (featureName == null || featureName.isBlank()) {
            throw new IllegalArgumentException("Feature name must be non-blank.");
        }
        if (measuredFeatureValue < 0.0) {
            throw new IllegalArgumentException("Feature measurement cannot be negative; received [" + measuredFeatureValue + "].");
        }
        currentFeatureMeasurementByName.put(featureName, measuredFeatureValue);
    }

    public int currentTotalGridCellCount() {
        return currentTotalGridCellCount;
    }

    public int currentEcosystemPlantSpeciesCellCount(int plantSpeciesIndex) {
        if (plantSpeciesIndex < 0 || plantSpeciesIndex >= ecosystemStatisticsArrayLength) {
            return 0;
        }
        return currentPlantSpeciesCellCountByIndex[plantSpeciesIndex];
    }

    public double currentEcosystemPlantSpeciesCoverageFraction(int plantSpeciesIndex) {
        if (plantSpeciesIndex < 0 || plantSpeciesIndex >= ecosystemStatisticsArrayLength) {
            return 0.0;
        }
        return currentPlantSpeciesCoverageFractionByIndex[plantSpeciesIndex];
    }

    public boolean isAnimalSpeciesCurrentlyActive(String animalSpeciesName) {
        if (animalSpeciesName == null || animalSpeciesName.isBlank()) {
            throw new IllegalArgumentException("Animal species name must be non-blank.");
        }
        return currentlyActiveAnimalSpecies.contains(animalSpeciesName);
    }

    private boolean evaluateRequirementGroup(RequirementGroup requirementGroup) {
        String logicalOperatorType = requirementGroup.logicalOperatorType();
        List<RequirementCondition> childRequirementConditions = requirementGroup.childRequirementConditions();
        if ("AND".equals(logicalOperatorType)) {
            for (RequirementCondition childRequirementCondition : childRequirementConditions) {
                if (!evaluateRequirementCondition(childRequirementCondition)) {
                    return false;
                }
            }
            return true;
        }
        if ("OR".equals(logicalOperatorType)) {
            for (RequirementCondition childRequirementCondition : childRequirementConditions) {
                if (evaluateRequirementCondition(childRequirementCondition)) {
                    return true;
                }
            }
            return false;
        }
        throw new IllegalArgumentException("Unsupported requirement group logical operator type [" + logicalOperatorType + "].");
    }

    private boolean evaluateRequirementCondition(RequirementCondition requirementCondition) {
        String conditionType = requirementCondition.conditionType();
        switch (conditionType) {
            case "coverage":
            case "group_coverage":
                return evaluateGroupCoverageRequirementCondition(requirementCondition);
            case "count":
                return evaluateGroupCountRequirementCondition(requirementCondition);
            case "dominance":
                return evaluateDominanceRequirementCondition(requirementCondition);
            default:
                throw new IllegalArgumentException("Unsupported animal requirement condition type [" + conditionType + "].");
        }
    }

    private boolean evaluateGroupCoverageRequirementCondition(RequirementCondition requirementCondition) {
        double measuredGroupCoverageFraction = calculateAggregateCoverageFractionAcrossSpeciesGroup(requirementCondition.interactingSpeciesGroup());
        return compareMeasuredValueAgainstThreshold(measuredGroupCoverageFraction, requirementCondition.comparisonOperator(), requirementCondition.requiredThresholdValue());
    }

    private boolean evaluateGroupCountRequirementCondition(RequirementCondition requirementCondition) {
        double measuredAggregateCellCount = calculateAggregateCellCountAcrossSpeciesGroup(requirementCondition.interactingSpeciesGroup());
        return compareMeasuredValueAgainstThreshold(measuredAggregateCellCount, requirementCondition.comparisonOperator(), requirementCondition.requiredThresholdValue());
    }

    private boolean evaluateDominanceRequirementCondition(RequirementCondition requirementCondition) {
        if (!"single_species".equals(requirementCondition.dominanceMode())) {
            throw new IllegalArgumentException(
                "Unsupported dominance mode [" + requirementCondition.dominanceMode() + "].");
        }
        if (requirementCondition.requiredThresholdValue() == null) {
            throw new IllegalArgumentException("Dominance requirement condition must declare a threshold value.");
        }
        for (int plantSpeciesIndex = 0; plantSpeciesIndex < ecosystemStatisticsArrayLength; plantSpeciesIndex++) {
            if (currentPlantSpeciesCoverageFractionByIndex[plantSpeciesIndex] >= requirementCondition.requiredThresholdValue()) {
                return true;
            }
        }
        return false;
    }

    private double calculateAggregateCoverageFractionAcrossSpeciesGroup(List<String> interactingSpeciesGroup) {
        double aggregateCoverageFraction = 0.0;
        for (String interactingPlantSpeciesName : interactingSpeciesGroup) {
            aggregateCoverageFraction += currentEcosystemPlantSpeciesCoverageFraction(resolvePlantIndexForPlantSpeciesName(interactingPlantSpeciesName));
        }
        return aggregateCoverageFraction;
    }

    private double calculateAggregateCellCountAcrossSpeciesGroup(List<String> interactingSpeciesGroup) {
        double aggregateCellCount = 0.0;
        for (String interactingPlantSpeciesName : interactingSpeciesGroup) {
            aggregateCellCount += currentEcosystemPlantSpeciesCellCount(resolvePlantIndexForPlantSpeciesName(interactingPlantSpeciesName));
        }
        return aggregateCellCount;
    }

    private boolean evaluateUnlockConditionNode(UnlockConditionNode unlockConditionNode) {
        if (unlockConditionNode == null) {
            throw new IllegalArgumentException("Unlock condition node cannot be null during evaluation.");
        }
        if (unlockConditionNode instanceof LogicalUnlockNode logicalUnlockNode) {
            return evaluateLogicalUnlockNode(logicalUnlockNode);
        }
        if (unlockConditionNode instanceof LeafUnlockCondition leafUnlockCondition) {
            return evaluateLeafUnlockCondition(leafUnlockCondition);
        }
        throw new IllegalArgumentException("Unsupported unlock condition node type [" + unlockConditionNode.getClass().getSimpleName() + "].");
    }

    private boolean evaluateLogicalUnlockNode(LogicalUnlockNode logicalUnlockNode) {
        String logicalOperator = logicalUnlockNode.logicalOperator();
        List<UnlockConditionNode> childUnlockConditionNodes = logicalUnlockNode.childUnlockConditionNodes();
        if ("AND".equals(logicalOperator)) {
            for (UnlockConditionNode childUnlockConditionNode : childUnlockConditionNodes) {
                if (!evaluateUnlockConditionNode(childUnlockConditionNode)) {
                    return false;
                }
            }
            return true;
        }
        if ("OR".equals(logicalOperator)) {
            for (UnlockConditionNode childUnlockConditionNode : childUnlockConditionNodes) {
                if (evaluateUnlockConditionNode(childUnlockConditionNode)) {
                    return true;
                }
            }
            return false;
        }
        throw new IllegalArgumentException("Unsupported unlock tree logical operator [" + logicalOperator + "].");
    }

    private boolean evaluateLeafUnlockCondition(LeafUnlockCondition leafUnlockCondition) {
        String conditionType = leafUnlockCondition.conditionType();
        switch (conditionType) {
            case "species_present":
                return currentlyActiveAnimalSpecies.contains(requireTextualLeafField(leafUnlockCondition, leafUnlockCondition.interactingSpeciesName(), "species_present", "interactingSpeciesName"));
            case "species_absent":
                return !currentlyActiveAnimalSpecies.contains(requireTextualLeafField(leafUnlockCondition, leafUnlockCondition.interactingSpeciesName(), "species_absent", "interactingSpeciesName"));
            case "coverage":
                return compareMeasuredValueAgainstThreshold(measuredCoverageForInteractingPlant(leafUnlockCondition), leafUnlockCondition.comparisonOperator(), leafUnlockCondition.requiredThresholdValue());
            case "count":
                return compareMeasuredValueAgainstThreshold(measuredCellCountForInteractingPlant(leafUnlockCondition), leafUnlockCondition.comparisonOperator(), leafUnlockCondition.requiredThresholdValue());
            case "feature_count":
                return compareMeasuredValueAgainstThreshold(
                    currentFeatureMeasurementByName.getOrDefault(
                        requireTextualLeafField(leafUnlockCondition, leafUnlockCondition.interactingFeatureName(), "feature_count", "interactingFeatureName"),
                        0.0),
                    leafUnlockCondition.comparisonOperator(),
                    leafUnlockCondition.requiredThresholdValue());
            case "event":
                return currentlyActiveWeatherEvents.contains(requireTextualLeafField(leafUnlockCondition, leafUnlockCondition.interactingEventName(), "event", "interactingEventName"));
            default:
                throw new IllegalArgumentException("Unsupported plant unlock leaf condition type [" + conditionType + "].");
        }
    }

    private double measuredCoverageForInteractingPlant(LeafUnlockCondition leafUnlockCondition) {
        String interactingPlantName = requireTextualLeafField(leafUnlockCondition, leafUnlockCondition.interactingPlantName(), "coverage", "interactingPlantName");
        return currentEcosystemPlantSpeciesCoverageFraction(resolvePlantIndexForPlantSpeciesName(interactingPlantName));
    }

    private double measuredCellCountForInteractingPlant(LeafUnlockCondition leafUnlockCondition) {
        String interactingPlantName = requireTextualLeafField(leafUnlockCondition, leafUnlockCondition.interactingPlantName(), "count", "interactingPlantName");
        return currentEcosystemPlantSpeciesCellCount(resolvePlantIndexForPlantSpeciesName(interactingPlantName));
    }

    private String requireTextualLeafField(
            LeafUnlockCondition leafUnlockCondition,
            String textualLeafFieldValue,
            String conditionType,
            String fieldDescription) {

        if (textualLeafFieldValue == null || textualLeafFieldValue.isBlank()) {
            throw new IllegalArgumentException(
                "Plant unlock leaf condition of type [" + conditionType + "] must declare its [" + fieldDescription + "] before evaluation.");
        }
        return textualLeafFieldValue;
    }

    private boolean compareMeasuredValueAgainstThreshold(double measuredValue, String comparisonOperator, Double requiredThresholdValue) {
        if (comparisonOperator == null || comparisonOperator.isBlank()) {
            throw new IllegalArgumentException("Comparison operator cannot be blank when a threshold value is present.");
        }
        if (requiredThresholdValue == null) {
            throw new IllegalArgumentException("Required threshold value cannot be null when a comparison operator is present.");
        }
        switch (comparisonOperator) {
            case ">=":
                return measuredValue >= requiredThresholdValue;
            case "<=":
                return measuredValue <= requiredThresholdValue;
            case ">":
                return measuredValue > requiredThresholdValue;
            case "<":
                return measuredValue < requiredThresholdValue;
            case "==":
                return measuredValue == requiredThresholdValue;
            default:
                throw new IllegalArgumentException("Unsupported comparison operator [" + comparisonOperator + "].");
        }
    }

    private int resolvePlantIndexForPlantSpeciesName(String plantSpeciesName) {
        Integer resolvedPlantSpeciesIndex = plantSpeciesNameToIndexMapping.get(plantSpeciesName);
        return resolvedPlantSpeciesIndex == null ? UNRESOLVED_PLANT_SPECIES_INDEX : resolvedPlantSpeciesIndex;
    }

    private int resolvePlantIndexForPlantSpeciesNameOrThrow(String plantSpeciesName) {
        int resolvedPlantSpeciesIndex = resolvePlantIndexForPlantSpeciesName(plantSpeciesName);
        if (resolvedPlantSpeciesIndex == UNRESOLVED_PLANT_SPECIES_INDEX) {
            throw new IllegalArgumentException(
                "Plant species name [" + plantSpeciesName + "] could not be resolved to a plant index through the Level Two mapping.");
        }
        return resolvedPlantSpeciesIndex;
    }

    private static Map<String, Integer> copyMappingIntoPreservedOrder(Map<String, Integer> sourcePlantSpeciesNameToIndexMapping) {
        Map<String, Integer> preservedOrderMapping = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> mappingEntry : sourcePlantSpeciesNameToIndexMapping.entrySet()) {
            preservedOrderMapping.put(mappingEntry.getKey(), mappingEntry.getValue());
        }
        return Collections.unmodifiableMap(preservedOrderMapping);
    }

    private void validatePlantSpeciesNameToIndexMapping() {
        for (Map.Entry<String, Integer> mappingEntry : plantSpeciesNameToIndexMapping.entrySet()) {
            String plantSpeciesName = mappingEntry.getKey();
            int plantSpeciesIndex = mappingEntry.getValue();
            if (plantSpeciesName == null || plantSpeciesName.isBlank()) {
                throw new IllegalArgumentException("Plant species name cannot be blank within the Level Two mapping.");
            }
            if (plantSpeciesIndex <= SimulationGridState.DEAD_PLANT_INDEX) {
                throw new IllegalArgumentException(
                    "Plant species [" + plantSpeciesName + "] must map to a positive plant index; received [" + plantSpeciesIndex + "].");
            }
            if (plantSpeciesIndex > Short.MAX_VALUE) {
                throw new IllegalArgumentException(
                    "Plant species [" + plantSpeciesName + "] maps to plant index [" + plantSpeciesIndex
                        + "], which exceeds the maximum storable plant index [" + Short.MAX_VALUE + "].");
            }
        }
    }

    private void validateEveryPlantUnlockRulePlantNameResolvesThroughTheMapping() {
        for (PlantUnlockRule plantUnlockRule : parsedPlantUnlockRules) {
            resolvePlantIndexForPlantSpeciesNameOrThrow(plantUnlockRule.plantName());
        }
    }

    private int calculateMaximumMappedPlantSpeciesIndex() {
        int maximumMappedPlantSpeciesIndex = 0;
        for (Integer mappedPlantSpeciesIndex : plantSpeciesNameToIndexMapping.values()) {
            maximumMappedPlantSpeciesIndex = Math.max(maximumMappedPlantSpeciesIndex, mappedPlantSpeciesIndex);
        }
        return maximumMappedPlantSpeciesIndex;
    }

    private static Map<String, Integer> createDefaultLevelTwoPlantSpeciesNameToIndexMapping() {
        Map<String, Integer> canonicalLevelTwoPlantSpeciesNameToIndexMapping = new LinkedHashMap<>();
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Grass", 1);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Rose Bush", 2);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Lavender", 3);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Dwarf Sunflower", 4);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Orange Blossom", 5);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Oak Tree", 6);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Glowcap Fungus", 7);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Blue Moss", 8);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Crimson Vine", 9);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Silver Fern", 10);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Purple Canopy Tree", 11);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Whiteveil Mycelium", 12);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Emberroot Tree", 13);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Moonpetal Lily", 14);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Ironthorn Shrub", 15);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Crystal Cactus", 16);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Mire Bloom", 17);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Razorgrass", 18);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Skyvine", 19);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Ghost Orchid", 20);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Amber Fern", 21);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Thornheart Bramble", 22);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Sporewood Tree", 23);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Sunshard Bloom", 24);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Ashroot Bramble", 25);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Living Topiary", 26);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Bloodbloom", 27);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Starcap Colony", 28);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Phoenix Bloom", 29);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Worldtree Sapling", 30);
        canonicalLevelTwoPlantSpeciesNameToIndexMapping.put("Stone Reed", 31);
        return Collections.unmodifiableMap(canonicalLevelTwoPlantSpeciesNameToIndexMapping);
    }
}