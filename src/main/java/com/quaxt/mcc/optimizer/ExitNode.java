package com.quaxt.mcc.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public record ExitNode(ArrayList<Integer> predecessors) implements Node {
    @Override
    public List<Integer> successors() {
        return Collections.emptyList();
    }
}
