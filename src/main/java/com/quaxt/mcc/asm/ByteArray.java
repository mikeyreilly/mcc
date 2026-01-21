package com.quaxt.mcc.asm;

public record ByteArray(long size, int alignment) implements TypeAsm {
    public String suffix() {
        return "q";
    }

    @Override
    public boolean isScalar() {
        return false;
    }

    @Override
    public boolean isInteger() {
        return false;
    }
}
