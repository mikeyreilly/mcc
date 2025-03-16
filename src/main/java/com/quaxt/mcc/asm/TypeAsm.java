package com.quaxt.mcc.asm;

public sealed interface TypeAsm permits PrimitiveTypeAsm, ByteArray {

    int size();

    default int alignment() {
        return size();
    }
}


