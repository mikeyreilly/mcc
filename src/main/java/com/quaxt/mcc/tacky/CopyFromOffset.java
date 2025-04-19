package com.quaxt.mcc.tacky;

public record CopyFromOffset(VarIr src, long offset, VarIr dst) implements InstructionIr {
}
