package com.quaxt.mcc.asm;

public record Memory(Reg reg, int offset) implements Operand {
    public Operand plus(int offset) {
        return new Memory(reg, this.offset + offset);
    }
}
