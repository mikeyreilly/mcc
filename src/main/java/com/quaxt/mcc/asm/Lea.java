package com.quaxt.mcc.asm;

public record Lea(Operand src, Operand dst) implements Instruction {}
