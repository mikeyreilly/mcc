package com.quaxt.mcc.tacky;

public record BitFieldSubObject(VarIr base, int byteOffset, int bitOffset,
                                int bitWidth) implements ExpResult {}
