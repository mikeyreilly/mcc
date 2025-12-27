package com.quaxt.mcc.asm;

public record Memory(IntegerReg reg, long offset) implements Operand {
    public Memory(IntegerReg reg, long offset){
        this.reg=reg;
        this.offset=offset;
    }
    public Operand plus(long offset) {
        return new Memory(reg, this.offset + offset);
    }
}
