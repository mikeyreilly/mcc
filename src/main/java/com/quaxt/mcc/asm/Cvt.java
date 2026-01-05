package com.quaxt.mcc.asm;
/*Convert With Truncation Scalar Double to SignedInteger*/
public record Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) implements Instruction {
    public Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) {
        assert(dstType.isInteger()?!(dst instanceof DoubleReg):!(dst instanceof IntegerReg));
        assert(srcType.isInteger()?!(src instanceof DoubleReg):!(src instanceof IntegerReg));
        this.srcType = srcType;
        this.dstType = dstType;
        this.src = src;
        this.dst = dst;
    }
}
