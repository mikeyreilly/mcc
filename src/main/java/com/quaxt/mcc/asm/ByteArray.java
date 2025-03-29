package com.quaxt.mcc.asm;

import com.quaxt.mcc.Err;

public record ByteArray(int size, int alignment) implements TypeAsm {
    public String suffix() {
        return "q";
    }
}
