package com.quaxt.mcc.asm;

public record Cmp(TypeAsm type, Operand subtrahend, Operand minuend) implements Instruction {
}
