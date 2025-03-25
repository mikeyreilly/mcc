package com.quaxt.mcc.asm;

public record MovZeroExtend(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) implements Instruction {
}
