package com.quaxt.mcc.asm;

import com.quaxt.mcc.Pair;
import com.quaxt.mcc.parser.Var;
import com.quaxt.mcc.semantic.FunType;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.InstructionIr;
import com.quaxt.mcc.tacky.TopLevel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FunctionIr implements AsmNode, TopLevelAsm, TopLevel {
    public final String name;
    public final boolean global;
    public boolean returnInMemory;
    public List<Instruction> instructions;
    public List<Var> type;
    public List<InstructionIr> instructionIrs;
    public FunType funType;
    public boolean inline;
    public IntegerReg[] calleeSavedRegs = new IntegerReg[0];
    public Pair<Integer, Integer> returnRegisters;
    public long stackSize;
    public boolean callsVaStart;
    public long stackAlignment;
    public int pos;
    public Map<String, Long> varTable;
    public Map<String, Reg> debugRegisterTable = Map.of();
    public List<DebugScope> debugScopes = List.of();
    public List<DebugLocal> debugLocals = List.of();
    public long[] instructionStackDeltas;
    public String[] frameBaseBoundaryLabels;
    public List<FrameBaseRange> frameBaseRanges = List.of();
    public String frameBaseLocListLabel;

    public FunctionIr(String name, boolean global, boolean returnInMemory,
                      List<Instruction> instructions,
                      Pair<Integer, Integer> returnRegisters, boolean callsVaStart,
                      int pos,
                      List<Var> type,
                      List<InstructionIr> instructionIrs,
                      FunType funType,
                      boolean inline) {
        this.name = name;
        this.global = global;
        this.returnInMemory = returnInMemory;
        this.instructions = instructions;
        this.returnRegisters = returnRegisters;
        this.callsVaStart = callsVaStart;
        this.pos = pos;
        this.type = type;
        this.instructionIrs = instructionIrs;
        this.funType = funType;
        this.inline = inline;
    }

    public Type returnType() {
        return funType.ret();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FunctionIr that)) return false;
        return global == that.global && returnInMemory == that.returnInMemory &&
                inline == that.inline && stackSize == that.stackSize &&
                callsVaStart == that.callsVaStart &&
                stackAlignment == that.stackAlignment && pos == that.pos &&
                Objects.equals(name, that.name) &&
                Objects.equals(instructions, that.instructions) &&
                Objects.equals(type, that.type) &&
                Objects.equals(instructionIrs, that.instructionIrs) &&
                Objects.equals(funType, that.funType) &&
                Objects.deepEquals(calleeSavedRegs, that.calleeSavedRegs) &&
                Objects.equals(returnRegisters, that.returnRegisters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, global, returnInMemory, instructions, type,
                instructionIrs, funType, inline,
                Arrays.hashCode(calleeSavedRegs), returnRegisters, stackSize,
                callsVaStart, stackAlignment, pos);
    }

    @Override
    public String toString() {
        return "FunctionAsm[" + "name=" + name + ", " + "global=" + global +
                ", " + "returnInMemory=" + returnInMemory + ", " +
                "instructionIrs=" + instructions + ']';
    }

}
