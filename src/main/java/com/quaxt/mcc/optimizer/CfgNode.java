package com.quaxt.mcc.optimizer;

import java.util.List;

public sealed interface CfgNode permits BasicBlock, EntryNode, ExitNode {
    List<CfgNode> successors();

    List<CfgNode> predecessors();

    int nodeId();
}
