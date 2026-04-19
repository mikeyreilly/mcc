package com.quaxt.mcc.asm;

public record IncomingStackArg(long offset) implements Operand {
    @Override
    public Operand plus(long offset) {
        return new IncomingStackArg(this.offset + offset);
    }
}
