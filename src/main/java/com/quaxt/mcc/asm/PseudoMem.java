package com.quaxt.mcc.asm;

public record PseudoMem(String identifier, long offset) implements Operand {
    public Operand plus(long offset) {
        return new PseudoMem(identifier, this.offset + offset);
    }
}
