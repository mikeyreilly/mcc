package com.quaxt.mcc.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public record ExitNode(ArrayList<Node> predecessors) implements Node {
    @Override
    public List<Node> successors() {
        return Collections.emptyList();
    }

    @Override
    public int nodeId() {
        return Integer.MAX_VALUE;
    }
}
