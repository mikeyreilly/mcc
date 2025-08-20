package com.quaxt.mcc.asm;

import com.quaxt.mcc.Err;

public enum PrimitiveTypeAsm implements TypeAsm {
    BYTE(1, "b", true), WORD(2, "w", true), LONGWORD(4, "l", true), QUADWORD(8, "q", true), FLOAT(4, "ss", false), DOUBLE(8, "sd", false);

    private final int size;
    private final String suffix;
    private final boolean isInteger;

    PrimitiveTypeAsm(int size, String suffix, boolean isInteger) {
        this.size = size;
        this.suffix = suffix;
        this.isInteger = isInteger;
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

    @Override
    public boolean isInteger() {
        return isInteger;
    }
}
