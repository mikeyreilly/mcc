package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.tacky.InstructionIr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public record EntryNode(ArrayList<Integer> successors) implements Node {
    @Override
    public List<Integer> predecessors() {
        return Collections.emptyList();
    }
}
