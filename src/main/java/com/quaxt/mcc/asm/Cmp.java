package com.quaxt.mcc.asm;

public record Cmp(Operand subtrahend, Operand minuend) implements Instruction {
}
