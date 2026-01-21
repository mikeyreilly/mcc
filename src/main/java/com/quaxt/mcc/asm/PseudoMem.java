package com.quaxt.mcc.asm;

public record PseudoMem(String identifier, long offset, int alignment) implements Operand {
    public PseudoMem(String identifier, long offset) {
        this(identifier, offset, 0);
    }
    public PseudoMem(String identifier, long offset, int alignment) {
        this.identifier=identifier;
        this.offset=offset;
        this.alignment=alignment;
    }
    public Operand plus(long offset) {
        return new PseudoMem(identifier, this.offset + offset, alignment);
    }
}
