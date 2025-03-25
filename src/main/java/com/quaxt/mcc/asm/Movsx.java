package com.quaxt.mcc.asm;

public record Movsx(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) implements Instruction {
}
