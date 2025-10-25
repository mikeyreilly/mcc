package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.ULONG;

public record TypeofT(Type innerType) implements TypeSpecifier, Type {

    @Override
    public boolean looseEquals(Type other) {
        return false;
    }
}
