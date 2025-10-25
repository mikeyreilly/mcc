package com.quaxt.mcc.tacky;

public record CopyBitsFromOffset(VarIr base, int byteOffset, int bitOffset,
                                 int bitWidth,
                                 VarIr dst) implements InstructionIr {}
