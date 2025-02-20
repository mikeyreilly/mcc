package com.quaxt.mcc.asm;
/*Convert Signed Integer to Scalar Double */
public record Cvtsi2sd(TypeAsm srcType, Operand src, Operand dst) implements Instruction {
}
