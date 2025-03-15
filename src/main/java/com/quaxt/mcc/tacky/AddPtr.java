package com.quaxt.mcc.tacky;

public record AddPtr(ValIr ptr, ValIr index, int scale, ValIr dst) implements InstructionIr {
}
