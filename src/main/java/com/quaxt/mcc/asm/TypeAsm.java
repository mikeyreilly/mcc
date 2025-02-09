package com.quaxt.mcc.asm;

public enum TypeAsm {
    LONGWORD(4), QUADWORD(8);

    private final int size;

    TypeAsm(int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }
}
