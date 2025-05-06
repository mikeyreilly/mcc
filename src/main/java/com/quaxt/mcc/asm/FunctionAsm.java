package com.quaxt.mcc.asm;

import com.quaxt.mcc.Pair;

import java.util.List;
import java.util.Objects;

public final class FunctionAsm implements AsmNode, TopLevelAsm {
    public final String name;
    public final boolean global;
    public final boolean returnInMemory;
    public final List<Instruction> instructions;
    public HardReg[] calleeSavedRegs = new HardReg[0];
    public Pair<Integer, Integer> returnRegisters;
    public FunctionAsm(String name, boolean global, boolean returnInMemory,
                       List<Instruction> instructions, Pair<Integer, Integer> returnRegisters) {
        this.name = name;
        this.global = global;
        this.returnInMemory = returnInMemory;
        this.instructions = instructions;
        this.returnRegisters = returnRegisters;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (FunctionAsm) obj;
        return Objects.equals(this.name, that.name) && this.global == that.global && this.returnInMemory == that.returnInMemory && Objects.equals(this.instructions, that.instructions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, global, returnInMemory, instructions);
    }

    @Override
    public String toString() {
        return "FunctionAsm[" + "name=" + name + ", " + "global=" + global +
                ", " + "returnInMemory=" + returnInMemory + ", " +
                "instructions=" + instructions + ']';
    }

}

