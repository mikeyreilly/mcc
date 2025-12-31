package com.quaxt.mcc.tacky;

public record CopyBitsToOffsetViaPointer(ValIr rval, VarIr base, int byteOffset,
                                         int bitOffset, int bitWidth) implements
        InstructionIr {}
