package com.quaxt.mcc.registerallocator;

import com.quaxt.mcc.asm.Instruction;

import java.util.List;

public class RegisterAllocator {

    /** p. 630 */
    public static void allocateRegisters(List<Instruction> instructions) {
        var interferenceGraph = buildGraph(instructions);
    }

    private static List<Node> buildGraph(List<Instruction> instructions) {
        return null;
    }
}
