package com.quaxt.mcc.tacky;

public record AddPtr(VarIr ptr, ValIr index, int scale, VarIr dst)
        implements InstructionIr {
}
