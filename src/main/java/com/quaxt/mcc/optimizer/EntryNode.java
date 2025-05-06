package com.quaxt.mcc.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public record EntryNode(ArrayList<CfgNode> successors) implements CfgNode {
    @Override
    public List<CfgNode> predecessors() {
        return Collections.emptyList();
    }

    @Override
    public int nodeId() {
        return 0;
    }
}
