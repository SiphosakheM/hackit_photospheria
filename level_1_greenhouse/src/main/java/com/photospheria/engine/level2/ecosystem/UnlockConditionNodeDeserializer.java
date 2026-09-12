package com.photospheria.engine.level2.ecosystem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UnlockConditionNodeDeserializer extends JsonDeserializer<UnlockConditionNode> {

    @Override
    public UnlockConditionNode deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode unlockConditionNode = jsonParser.getCodec().readTree(jsonParser);
        return deserializeUnlockConditionNode(unlockConditionNode);
    }

    private UnlockConditionNode deserializeUnlockConditionNode(JsonNode unlockConditionNode) {
        if (unlockConditionNode.hasNonNull("op")) {
            return deserializeLogicalUnlockNode(unlockConditionNode);
        }
        return deserializeLeafUnlockCondition(unlockConditionNode);
    }

    private LogicalUnlockNode deserializeLogicalUnlockNode(JsonNode logicalUnlockNode) {
        String logicalOperator = logicalUnlockNode.get("op").asText();
        JsonNode childUnlockConditionNodesNode = logicalUnlockNode.get("children");
        if (childUnlockConditionNodesNode == null || !childUnlockConditionNodesNode.isArray()) {
            throw new IllegalArgumentException(
                "Logical unlock node with operator [" + logicalOperator + "] must declare a children array.");
        }
        List<UnlockConditionNode> childUnlockConditionNodes = new ArrayList<>();
        for (JsonNode individualChildUnlockConditionNode : childUnlockConditionNodesNode) {
            childUnlockConditionNodes.add(deserializeUnlockConditionNode(individualChildUnlockConditionNode));
        }
        return new LogicalUnlockNode(logicalOperator, childUnlockConditionNodes);
    }

    private LeafUnlockCondition deserializeLeafUnlockCondition(JsonNode leafUnlockConditionNode) {
        String conditionType = readRequiredTextField(leafUnlockConditionNode, "type");
        String interactingSpeciesName = readNullableTextField(leafUnlockConditionNode, "species");
        String interactingPlantName = readNullableTextField(leafUnlockConditionNode, "plant");
        String interactingFeatureName = readNullableTextField(leafUnlockConditionNode, "feature");
        String interactingEventName = readNullableTextField(leafUnlockConditionNode, "event");
        String comparisonOperator = readNullableTextField(leafUnlockConditionNode, "operator");
        Double requiredThresholdValue = readNullableDecimalField(leafUnlockConditionNode, "value");
        return new LeafUnlockCondition(
            conditionType,
            interactingSpeciesName,
            interactingPlantName,
            interactingFeatureName,
            interactingEventName,
            comparisonOperator,
            requiredThresholdValue);
    }

    private String readRequiredTextField(JsonNode parentNode, String fieldNameToRead) {
        JsonNode textValueNode = parentNode.get(fieldNameToRead);
        if (textValueNode == null || textValueNode.isNull() || textValueNode.asText().isBlank()) {
            throw new IllegalArgumentException(
                "Unlock condition node is missing its required text field [" + fieldNameToRead + "].");
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