package com.quaxt.mcc.asm;

public record Indexed(IntegerReg base, IntegerReg index, int scale) implements Operand {}
