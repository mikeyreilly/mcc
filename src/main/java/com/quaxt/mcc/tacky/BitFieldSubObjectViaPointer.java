package com.quaxt.mcc.tacky;

public record BitFieldSubObjectViaPointer(VarIr ptr, int memberOffset,
                                          int bitOffset,
                                          int bitWidth) implements ExpResult {}
