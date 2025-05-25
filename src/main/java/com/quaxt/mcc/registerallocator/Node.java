package com.quaxt.mcc.registerallocator;

import com.quaxt.mcc.asm.Operand;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Node {
    public Node() {}

    Operand operand;
    List<Node> neighbours;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Node node)) return false;
        return Objects.equals(operand, node.operand) ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(operand);
    }

    double spillCost;
    int color; // color -1 means no color
    boolean pruned;

    @Override
    public String toString() {
        return "Node{" + "color=" + color + ",operand=" + operand + ", " +
                "neighbours=" + neighbours.stream().map(n -> String.valueOf(n.operand)).collect(Collectors.joining(", ")) + ", spillCost=" + spillCost + ", pruned=" + pruned + '}';
    }
}
