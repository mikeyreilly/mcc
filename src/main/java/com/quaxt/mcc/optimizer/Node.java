package com.quaxt.mcc.optimizer;

import java.util.ArrayList;
import java.util.List;

public sealed interface Node permits BasicBlock, EntryNode, ExitNode {
    List<Node> successors();

    List<Node> predecessors();

    int nodeId();
}
