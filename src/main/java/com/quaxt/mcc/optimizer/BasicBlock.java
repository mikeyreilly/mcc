package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.tacky.InstructionIr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public final class BasicBlock implements Node {
    private final int nodeId;
    private final List<InstructionIr> instructions;
    private final ArrayList<Integer> predecessors;
    private final ArrayList<Integer> successors;

    public BasicBlock(int nodeId, List<InstructionIr> instructions, ArrayList<Integer> predecessors, ArrayList<Integer> successors) {
        this.nodeId = nodeId;
        this.instructions = instructions;
        this.predecessors = predecessors;
        this.successors = successors;
    }

    public int nodeId() {return nodeId;}

    public List<InstructionIr> instructions() {return instructions;}

    public ArrayList<Integer> predecessors() {return predecessors;}

    public ArrayList<Integer> successors() {return successors;}

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (BasicBlock) obj;
        return this.nodeId == that.nodeId && Objects.equals(this.instructions, that.instructions) && Objects.equals(this.predecessors, that.predecessors) && Objects.equals(this.successors, that.successors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, instructions, predecessors, successors);
    }

    @Override
    public String toString() {
        return "BasicBlock[" + "nodeId=" + nodeId + ", " + "instructions=" + instructions + ", " + "predecessors=" + predecessors + ", " + "successors=" + successors + ']';
    }
}
