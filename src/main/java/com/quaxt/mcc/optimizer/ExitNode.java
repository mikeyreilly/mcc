package com.quaxt.mcc.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public record ExitNode(ArrayList<CfgNode> predecessors) implements CfgNode {
    @Override
    public List<CfgNode> successors() {
        return Collections.emptyList();
    }

    @Override
    public int nodeId() {
        return Integer.MAX_VALUE;
    }
}
