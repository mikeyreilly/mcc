package com.quaxt.mcc.tacky;

public record CopyToOffset(ValIr src, VarIr dst, int offset) implements InstructionIr {
}
