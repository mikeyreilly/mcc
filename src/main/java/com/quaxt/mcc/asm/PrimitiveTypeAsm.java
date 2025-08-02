package com.quaxt.mcc.asm;

import com.quaxt.mcc.Err;

public enum PrimitiveTypeAsm implements TypeAsm {
    BYTE(1, "b"), WORD(2, "w"), LONGWORD(4, "l"), QUADWORD(8, "q"), DOUBLE(8, "sd");

    private final int size;
    private final String suffix;

    PrimitiveTypeAsm(int size, String suffix) {
        this.size = size;
        this.suffix = suffix;
    }

    public long size() {
        return size;
    }

    public String suffix() {
        return suffix;
    }

    @Override
    public boolean isScalar() {
        return true;
    }
}
