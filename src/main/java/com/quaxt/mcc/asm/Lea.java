package com.quaxt.mcc.asm;

public record Lea(Operand src, Operand dst) implements Instruction {
    public Lea(Operand src, Operand dst){
        this.src=src;
        this.dst=dst;
    }
}
