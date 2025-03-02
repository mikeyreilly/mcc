package com.quaxt.mcc.asm;

public record Memory(Reg reg, int offset) implements Operand {}
