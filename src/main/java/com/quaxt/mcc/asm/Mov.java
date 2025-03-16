package com.quaxt.mcc.asm;

public record Mov(TypeAsm type, Operand src, Operand dst) implements Instruction {
}
