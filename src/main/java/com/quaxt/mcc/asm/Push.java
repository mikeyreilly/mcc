package com.quaxt.mcc.asm;

public record Push(Operand operand) implements Instruction {
    public Push(Operand operand){
        this.operand=operand;
    }
}
