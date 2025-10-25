package com.quaxt.mcc.tacky;

public record CopyBitsToOffset(ValIr rval, VarIr base, long  byteOffset,
                               int bitOffset,
                               int bitWidth) implements InstructionIr {}
