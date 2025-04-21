package com.quaxt.mcc.asm;

public record Data(String identifier, int offset) implements Operand {
    public Data(String identifier, int offset) {
        this.identifier = identifier;
        this.offset = offset;
    }
}
