package com.quaxt.mcc.asm;
/*Convert With Truncation Scalar Double to SignedInteger*/
public record Cvttsd2si(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) implements Instruction {
}
