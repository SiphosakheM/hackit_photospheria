package com.photospheria.engine.level2.ecosystem;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = UnlockConditionNodeDeserializer.class)
public sealed interface UnlockConditionNode permits LogicalUnlockNode, LeafUnlockCondition {
}