package com.photospheria.engine.level2.ecosystem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RequirementConditionDeserializer extends JsonDeserializer<RequirementCondition> {

    @Override
    public RequirementCondition deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode requirementConditionNode = jsonParser.getCodec().readTree(jsonParser);
        String conditionType = readRequiredTextField(requirementConditionNode, "type");
        List<String> allInteractingSpeciesNames = readAllInteractingSpeciesNames(requirementConditionNode);
        String comparisonOperator = readNullableTextField(requirementConditionNode, "operator");
        Double requiredThresholdValue = readNullableDecimalField(requirementConditionNode, "threshold");
        String dominanceMode = readNullableTextField(requirementConditionNode, "mode");
        return new RequirementCondition(conditionType, allInteractingSpeciesNames, comparisonOperator, requiredThresholdValue, dominanceMode);
    }

    private List<String> readAllInteractingSpeciesNames(JsonNode requirementConditionNode) {
        List<String> allInteractingSpeciesNames = new ArrayList<>();
        collectSpeciesNamesInto(requirementConditionNode, "species", allInteractingSpeciesNames);
        collectSpeciesNamesInto(requirementConditionNode, "species_group", allInteractingSpeciesNames);
        return List.copyOf(allInteractingSpeciesNames);
    }

    private void collectSpeciesNamesInto(
            JsonNode requirementConditionNode,
            String speciesCollectionFieldName,
            List<String> collectedSpeciesNames) {

        JsonNode speciesCollectionNode = requirementConditionNode.get(speciesCollectionFieldName);
        if (speciesCollectionNode == null || speciesCollectionNode.isNull()) {
            return;
        }
        if (speciesCollectionNode.isArray()) {
            for (JsonNode individualSpeciesNode : speciesCollectionNode) {
                collectedSpeciesNames.add(individualSpeciesNode.asText());
            }
            return;
        }
        collectedSpeciesNames.add(speciesCollectionNode.asText());
    }

    private String readRequiredTextField(JsonNode parentNode, String fieldNameToRead) {
        JsonNode textValueNode = parentNode.get(fieldNameToRead);
        if (textValueNode == null || textValueNode.isNull() || textValueNode.asText().isBlank()) {
            throw new IllegalArgumentException(
                "Requirement condition is missing its required text field [" + fieldNameToRead + "].");
        }
        return textValueNode.asText();
    }

    private String readNullableTextField(JsonNode parentNode, String fieldNameToRead) {
        JsonNode textValueNode = parentNode.get(fieldNameToRead);
        if (textValueNode == null || textValueNode.isNull()) {
            return null;
        }
        return textValueNode.asText();
    }

    private Double readNullableDecimalField(JsonNode parentNode, String fieldNameToRead) {
        JsonNode decimalValueNode = parentNode.get(fieldNameToRead);
        if (decimalValueNode == null || decimalValueNode.isNull()) {
            return null;
        }
        return decimalValueNode.asDouble();
    }
}