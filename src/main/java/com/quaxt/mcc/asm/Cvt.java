package com.quaxt.mcc.asm;
/*Convert With Truncation Scalar Double to SignedInteger*/
public record Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) implements Instruction {
}
