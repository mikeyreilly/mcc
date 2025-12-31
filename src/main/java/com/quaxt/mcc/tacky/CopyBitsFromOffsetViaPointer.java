package com.quaxt.mcc.tacky;

public record CopyBitsFromOffsetViaPointer(VarIr ptr, int byteOffset,
                                           int bitOffset, int bitWidth,
                                           VarIr dst) implements InstructionIr {}
