package com.quaxt.mcc.asm;

public record Memory(Reg reg, long offset) implements Operand {
    public Operand plus(long offset) {
        return new Memory(reg, this.offset + offset);
    }
}
