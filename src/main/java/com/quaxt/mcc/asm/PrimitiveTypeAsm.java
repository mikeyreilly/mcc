package com.quaxt.mcc.asm;

public enum PrimitiveTypeAsm implements TypeAsm{
    LONGWORD(4), QUADWORD(8), DOUBLE(8);

    private final int size;

    PrimitiveTypeAsm(int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }
}