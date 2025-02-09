package com.quaxt.mcc.asm;

public record Mov(TypeAsm type, Operand src, Operand dst) implements Instruction {
    public Mov (TypeAsm type, Operand src, Operand dst) {
        this.src=src;
        this.dst=dst;
        this.type=type;
    }
}
