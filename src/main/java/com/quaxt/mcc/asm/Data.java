package com.quaxt.mcc.asm;

public record Data(String identifier, long offset) implements Operand {
    public Data(String identifier, long offset) {
        this.identifier = identifier;
        this.offset = offset;
    }
}
