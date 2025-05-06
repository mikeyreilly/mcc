package com.quaxt.mcc.registerallocator;

import com.quaxt.mcc.asm.Operand;

import java.util.List;

public record Node(Operand operand, List<Node> neighbours, double spillCost,
                   int color, // color -1 means no color
                   boolean pruned) {}
