package com.photospheria.engine.level2.ecosystem;

import java.util.List;

public record LogicalUnlockNode(
        String logicalOperator,
        List<UnlockConditionNode> childUnlockConditionNodes) implements UnlockConditionNode {

    public LogicalUnlockNode {
        if (logicalOperator == null || logicalOperator.isBlank()) {
            throw new IllegalArgumentException("Logical unlock node must declare a non-blank logical operator.");
        }
        if (childUnlockConditionNodes == null || childUnlockConditionNodes.isEmpty()) {
            throw new IllegalArgumentException(
                "Logical unlock node with operator [" + logicalOperator
                    + "] must declare at least one child unlock condition node.");
        }
        childUnlockConditionNodes = List.copyOf(childUnlockConditionNodes);
    }
}