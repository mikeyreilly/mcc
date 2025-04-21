package com.quaxt.mcc.asm;

public record ByteArray(int size, int alignment) implements TypeAsm {
    public String suffix() {
        return "q";
    }

    @Override
    public boolean isScalar() {
        return false;
    }
}
