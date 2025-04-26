package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.Err;
import com.quaxt.mcc.tacky.InstructionIr;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record BasicBlock(int nodeId, List<InstructionIr> instructions,
                         ArrayList<Node> predecessors,
                         ArrayList<Node> successors) implements Node {

    // helper for toString. The default toString method would cause stackoverflow
    private static String stringifyNodes(ArrayList<Node> nodes) {
        return nodes.stream().map(n -> String.valueOf(n.nodeId())).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return "BasicBlock{" + "nodeId=" + nodeId + ", instructions=" + instructions + ", predecessors=" + stringifyNodes(predecessors) + ", successors=" + stringifyNodes(successors) + '}';
    }

    public boolean equals(Object o) {
        return o instanceof BasicBlock bb && bb.nodeId == nodeId;
    }
}
