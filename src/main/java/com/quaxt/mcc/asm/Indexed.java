package com.quaxt.mcc.asm;

public record Indexed(HardReg base, HardReg index, int scale) implements Operand {}
