package com.quaxt.mcc.asm;

public record PseudoMem(String identifier, long offset) implements Operand {
    public PseudoMem(String identifier, long offset) {
        this.identifier=identifier;
        this.offset=offset;
    }
    public Operand plus(long offset) {
        return new PseudoMem(identifier, this.offset + offset);
    }
}
