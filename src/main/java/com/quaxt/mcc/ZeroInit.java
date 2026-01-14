package com.quaxt.mcc;

import com.quaxt.mcc.parser.Initializer;
import com.quaxt.mcc.semantic.Type;

public record ZeroInit(long bytes) implements StaticInit, Initializer {
    public ZeroInit(long bytes){
        this.bytes=bytes;
    }

    @Override
    public Type type() {
        return null;
    }
}
