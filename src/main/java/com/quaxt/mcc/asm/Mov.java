package com.quaxt.mcc.asm;

public record Mov(TypeAsm type, Operand src,
                  Operand dst) implements Instruction {
    public Mov(TypeAsm type, Operand src, Operand dst) {
        this.type = type;
        this.src = src;
        this.dst = dst;
        dst=dst;
    }
}
