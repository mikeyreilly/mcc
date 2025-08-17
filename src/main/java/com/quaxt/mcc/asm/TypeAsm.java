package com.quaxt.mcc.asm;

import com.quaxt.mcc.Err;

public sealed interface TypeAsm permits PrimitiveTypeAsm, ByteArray {

    long size();

    default long alignment() {
        return size();
    }

    String suffix();

    boolean isScalar();

    boolean isInteger();
}


