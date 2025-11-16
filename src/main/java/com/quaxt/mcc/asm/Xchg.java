package com.quaxt.mcc.asm;

public record Xchg(TypeAsm type, Operand src,
                   Operand dst) implements Instruction {
    public Xchg(TypeAsm type, Operand src, Operand dst) {
        this.type = type;
        this.src = src;
        this.dst = dst;
    }
}
