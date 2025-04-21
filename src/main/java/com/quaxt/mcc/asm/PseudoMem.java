package com.quaxt.mcc.asm;

public record PseudoMem(String identifier, int offset) implements Operand {
    public Operand plus(int offset) {
        return new PseudoMem(identifier, this.offset + offset);
    }
}
