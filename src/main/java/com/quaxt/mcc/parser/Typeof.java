package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Typeof(Exp exp) implements TypeSpecifier, Type {

    @Override
    public boolean looseEquals(Type other) {
        return false;
    }
}
