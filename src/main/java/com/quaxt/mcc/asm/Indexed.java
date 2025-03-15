package com.quaxt.mcc.asm;

public record Indexed(Reg base, Reg index, int scale) implements Operand {}
