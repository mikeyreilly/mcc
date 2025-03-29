package com.quaxt.mcc.asm;

import com.quaxt.mcc.Err;

public sealed interface TypeAsm permits PrimitiveTypeAsm, ByteArray {

    int size();

    default int alignment() {
        return size();
    }

    String suffix();
}


