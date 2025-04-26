package com.quaxt.mcc.asm;

public record ByteArray(long size, long alignment) implements TypeAsm {
    public String suffix() {
        return "q";
    }

    @Override
    public boolean isScalar() {
        return false;
    }
}
