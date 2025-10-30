package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Primitive;
import com.quaxt.mcc.semantic.Structure;
import com.quaxt.mcc.semantic.Type;

public record Offsetof(Structure structure, String member) implements Exp {
    @Override
    public Type type() {
        return Primitive.ULONG;
    }
}
