package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Primitive;
import com.quaxt.mcc.semantic.Structure;
import com.quaxt.mcc.semantic.Type;

import java.util.ArrayList;

public record Offsetof(Structure structure, ArrayList<OffsetofComponent> designators) implements Exp {
    @Override
    public Type type() {
        return Primitive.ULONG;
    }
}
