package com.photospheria.engine.level2.ecosystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class EcosystemRulesParsingTest {

    @Test
    void nectarisComplexOrRequirementWithGroupCoverageAndEffectModifiersParsesCorrectly() throws IOException {
        AnimalSpecies nectarisSpecies = findAnimalSpeciesByIdentifier(parseAllAnimalSpecies(), "nectaris");

        assertEquals("nectaris", nectarisSpecies.animalIdentifier());
        assertEquals("Nectaris", nectarisSpecies.animalDisplayName());

        RequirementGroup nectarisRequirementGroup = nectarisSpecies.speciesRequirementGroup();
        assertEquals("OR", nectarisRequirementGroup.logicalOperatorType());
        assertEquals(2, nectarisRequirementGroup.childRequirementConditions().size());

        RequirementCondition lavenderCoverageCondition = nectarisRequirementGroup.childRequirementConditions().get(0);
        assertEquals("coverage", lavenderCoverageCondition.conditionType());
        assertEquals(List.of("Lavender"), lavenderCoverageCondition.interactingSpeciesGroup());
        assertEquals(">=", lavenderCoverageCondition.comparisonOperator());
        assertEquals(0.02, lavenderCoverageCondition.requiredThresholdValue(), 0.000001);

        RequirementCondition pollinationPlantGroupCoverageCondition = nectarisRequirementGroup.childRequirementConditions().get(1);
        assertEquals("group_coverage", pollinationPlantGroupCoverageCondition.conditionType());
        assertEquals(
            List.of("Lavender", "Rose Bush", "Dwarf Sunflower", "Orange Blossom"),
            pollinationPlantGroupCoverageCondition.interactingSpeciesGroup());
        assertEquals(">=", pollinationPlantGroupCoverageCondition.comparisonOperator());
        assertEquals(0.02, pollinationPlantGroupCoverageCondition.requiredThresholdValue(), 0.000001);

        List<EcosystemEffect> nectarisEcosystemEffects = nectarisSpecies.speciesEcosystemEffects();
        assertEquals(2, nectarisEcosystemEffects.size());

        EcosystemEffect spreadRateEffect = nectarisEcosystemEffects.get(0);
        assertEquals("spread_rate", spreadRateEffect.effectType());
        assertEquals("Pollination Plants", spreadRateEffect.effectTargetName());
        assertEquals(1.5, spreadRateEffect.effectMultiplierValue(), 0.000001);
        assertEquals("multiply", spreadRateEffect.effectApplicationMode());

        EcosystemEffect maturationRateEffect = nectarisEcosystemEffects.get(1);
        assertEquals("maturation_rate", maturationRateEffect.effectType());
        assertEquals("Flowering Plants", maturationRateEffect.effectTargetName());
        assertEquals(1.1, maturationRateEffect.effectMultiplierValue(), 0.000001);
        assertEquals("multiply", maturationRateEffect.effectApplicationMode());
    }

    @Test
    void singleStringSpeciesCountConditionsNormaliseIntoSingletonSpeciesGroups() throws IOException {
        AnimalSpecies barkskipsSpecies = findAnimalSpeciesByIdentifier(parseAllAnimalSpecies(), "barkskips");

        RequirementGroup barkskipsRequirementGroup = barkskipsSpecies.speciesRequirementGroup();
        assertEquals("OR", barkskipsRequirementGroup.logicalOperatorType());

        RequirementCondition oakTreeCountCondition = barkskipsRequirementGroup.childRequirementConditions().get(0);
        assertEquals("count", oakTreeCountCondition.conditionType());
        assertEquals(List.of("Oak Tree"), oakTreeCountCondition.interactingSpeciesGroup());
        assertEquals(">=", oakTreeCountCondition.comparisonOperator());
        assertEquals(8, oakTreeCountCondition.requiredThresholdValue(), 0.0);

        RequirementCondition worldtreeSaplingCountCondition = barkskipsRequirementGroup.childRequirementConditions().get(1);
        assertEquals("count", worldtreeSaplingCountCondition.conditionType());
        assertEquals(List.of("Worldtree Sapling"), worldtreeSaplingCountCondition.interactingSpeciesGroup());
        assertEquals(1, worldtreeSaplingCountCondition.requiredThresholdValue(), 0.0);
    }

    @Test
    void dominanceConditionParsesDominanceModeAndThresholdWithoutOperatorOrSpeciesList() throws IOException {
        AnimalSpecies monocryxSpecies = findAnimalSpeciesByIdentifier(parseAllAnimalSpecies(), "monocryx");

        assertEquals("AND", monocryxSpecies.speciesRequirementGroup().logicalOperatorType());

        RequirementCondition dominanceCondition = monocryxSpecies.speciesRequirementGroup().childRequirementConditions().get(0);
        assertEquals("dominance", dominanceCondition.conditionType());
        assertEquals("single_species", dominanceCondition.dominanceMode());
        assertEquals(0.5, dominanceCondition.requiredThresholdValue(), 0.000001);
        assertNull(dominanceCondition.comparisonOperator());
        assertTrue(dominanceCondition.interactingSpeciesGroup().isEmpty());
    }

    @Test
    void allTenAnimalSpeciesFromTheOfficialDataFileParseSuccessfully() throws IOException {
        assertEquals(10, parseAllAnimalSpecies().size());
    }

    @Test
    void blueMossUnlockTreeParsesAsAndLogicNodeWithThreeLeafConditions() throws IOException {
        PlantUnlockRule blueMossUnlockRule = findPlantUnlockRuleForPlantName(parseAllPlantUnlockRules(), "Blue Moss");

        assertInstanceOf(LogicalUnlockNode.class, blueMossUnlockRule.unlockConditionNode());
        LogicalUnlockNode blueMossRootLogicNode = (LogicalUnlockNode) blueMossUnlockRule.unlockConditionNode();
        assertEquals("AND", blueMossRootLogicNode.logicalOperator());
        assertEquals(3, blueMossRootLogicNode.childUnlockConditionNodes().size());

        assertSpeciesPresentLeafCondition(blueMossRootLogicNode.childUnlockConditionNodes().get(0), "Loamcrawlers");
        assertPlantCoverageLeafCondition(blueMossRootLogicNode.childUnlockConditionNodes().get(1), "Grass", ">", 0.03);
        assertPlantCoverageLeafCondition(blueMossRootLogicNode.childUnlockConditionNodes().get(2), "Rose Bush", ">", 0.01);
    }

    @Test
    void orangeBlossomUnlockTreeParsesNestedOrLogicNodeBeneathAndLogicNode() throws IOException {
        PlantUnlockRule orangeBlossomUnlockRule = findPlantUnlockRuleForPlantName(parseAllPlantUnlockRules(), "Orange Blossom");

        assertInstanceOf(LogicalUnlockNode.class, orangeBlossomUnlockRule.unlockConditionNode());
        LogicalUnlockNode orangeBlossomRootLogicNode = (LogicalUnlockNode) orangeBlossomUnlockRule.unlockConditionNode();
        assertEquals("AND", orangeBlossomRootLogicNode.logicalOperator());
        assertEquals(2, orangeBlossomRootLogicNode.childUnlockConditionNodes().size());

        UnlockConditionNode nestedPollinatorOrNode = orangeBlossomRootLogicNode.childUnlockConditionNodes().get(0);
        assertInstanceOf(LogicalUnlockNode.class, nestedPollinatorOrNode);
        LogicalUnlockNode nestedPollinatorOrLogicNode = (LogicalUnlockNode) nestedPollinatorOrNode;
        assertEquals("OR", nestedPollinatorOrLogicNode.logicalOperator());
        assertEquals(2, nestedPollinatorOrLogicNode.childUnlockConditionNodes().size());
        assertSpeciesPresentLeafCondition(nestedPollinatorOrLogicNode.childUnlockConditionNodes().get(0), "Nectaris");
        assertSpeciesPresentLeafCondition(nestedPollinatorOrLogicNode.childUnlockConditionNodes().get(1), "Solwings");

        assertPlantCoverageLeafCondition(orangeBlossomRootLogicNode.childUnlockConditionNodes().get(1), "Rose Bush", ">", 0.02);
    }

    @Test
    void featureCountAndEventLeafConditionsParseTheirDomainSpecificNames() throws IOException {
        List<PlantUnlockRule> allPlantUnlockRules = parseAllPlantUnlockRules();

        LeafUnlockCondition glowcapFungusFeatureCountCondition = (LeafUnlockCondition)
            ((LogicalUnlockNode) findPlantUnlockRuleForPlantName(allPlantUnlockRules, "Glowcap Fungus").unlockConditionNode())
                .childUnlockConditionNodes().get(1);
        assertEquals("feature_count", glowcapFungusFeatureCountCondition.conditionType());
        assertEquals("dead_matter", glowcapFungusFeatureCountCondition.interactingFeatureName());
        assertEquals(">", glowcapFungusFeatureCountCondition.comparisonOperator());
        assertEquals(0.05, glowcapFungusFeatureCountCondition.requiredThresholdValue(), 0.000001);

        LeafUnlockCondition crystalCactusEventCondition = (LeafUnlockCondition)
            ((LogicalUnlockNode) findPlantUnlockRuleForPlantName(allPlantUnlockRules, "Crystal Cactus").unlockConditionNode())
                .childUnlockConditionNodes().get(0);
        assertEquals("event", crystalCactusEventCondition.conditionType());
        assertEquals("Drought", crystalCactusEventCondition.interactingEventName());
    }

    @Test
    void speciesAbsentLeafConditionParsesWithAbsentThresholdAndOperator() throws IOException {
        LeafUnlockCondition monocryxAbsenceCondition = (LeafUnlockCondition)
            ((LogicalUnlockNode) findPlantUnlockRuleForPlantName(parseAllPlantUnlockRules(), "Amber Fern").unlockConditionNode())
                .childUnlockConditionNodes().get(2);
        assertEquals("species_absent", monocryxAbsenceCondition.conditionType());
        assertEquals("Monocryx", monocryxAbsenceCondition.interactingSpeciesName());
        assertNull(monocryxAbsenceCondition.comparisonOperator());
        assertNull(monocryxAbsenceCondition.requiredThresholdValue());
    }

    @Test
    void allTwentySixPlantUnlockRulesFromTheOfficialDataFileParseSuccessfully() throws IOException {
        assertEquals(26, parseAllPlantUnlockRules().size());
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

    private AnimalSpecies findAnimalSpeciesByIdentifier(List<AnimalSpecies> animalSpeciesList, String expectedAnimalIdentifier) {
        return animalSpeciesList.stream()
            .filter(animalSpecies -> animalSpecies.animalIdentifier().equals(expectedAnimalIdentifier))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No animal species with identifier [" + expectedAnimalIdentifier + "] was parsed."));
    }

    private PlantUnlockRule findPlantUnlockRuleForPlantName(List<PlantUnlockRule> plantUnlockRuleList, String expectedPlantName) {
        return plantUnlockRuleList.stream()
            .filter(plantUnlockRule -> plantUnlockRule.plantName().equals(expectedPlantName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No plant unlock rule for plant name [" + expectedPlantName + "] was parsed."));
    }

    private void assertSpeciesPresentLeafCondition(UnlockConditionNode unlockConditionNode, String expectedInteractingSpeciesName) {
        assertInstanceOf(LeafUnlockCondition.class, unlockConditionNode);
        LeafUnlockCondition speciesPresentLeafCondition = (LeafUnlockCondition) unlockConditionNode;
        assertEquals("species_present", speciesPresentLeafCondition.conditionType());
        assertEquals(expectedInteractingSpeciesName, speciesPresentLeafCondition.interactingSpeciesName());
    }

    private void assertPlantCoverageLeafCondition(
            UnlockConditionNode unlockConditionNode,
            String expectedInteractingPlantName,
            String expectedComparisonOperator,
            double expectedRequiredThresholdValue) {

        assertInstanceOf(LeafUnlockCondition.class, unlockConditionNode);
        LeafUnlockCondition plantCoverageLeafCondition = (LeafUnlockCondition) unlockConditionNode;
        assertEquals("coverage", plantCoverageLeafCondition.conditionType());
        assertEquals(expectedInteractingPlantName, plantCoverageLeafCondition.interactingPlantName());
        assertEquals(expectedComparisonOperator, plantCoverageLeafCondition.comparisonOperator());
        assertEquals(expectedRequiredThresholdValue, plantCoverageLeafCondition.requiredThresholdValue(), 0.000001);
    }
}