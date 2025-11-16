package com.quaxt.mcc.asm;

public record Movsx(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) implements Instruction {
    public Movsx(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst){
        this.srcType = srcType;
        this.dstType = dstType;
        this.src = src;
        this.dst = dst;
        dst=dst;
    }
}
