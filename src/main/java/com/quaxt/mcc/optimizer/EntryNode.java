package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.tacky.InstructionIr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public record EntryNode(ArrayList<Node> successors) implements Node {
    @Override
    public List<Node> predecessors() {
        return Collections.emptyList();
    }

    @Override
    public int nodeId() {
        return 0;
    }
}
