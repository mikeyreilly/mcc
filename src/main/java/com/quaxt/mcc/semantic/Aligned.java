package com.quaxt.mcc.semantic;

import com.quaxt.mcc.parser.Exp;

public record Aligned(Type inner, Exp alignment) implements Type {

    public Aligned(Type inner, Exp alignment) {
        assert (!(inner instanceof FunType));
        this.inner = inner;
        this.alignment = alignment;
    }

    @Override
    public boolean looseEquals(Type other) {
        return this.equals(other);
    }

     public boolean isSigned() {
        return inner.isSigned();
    }

     public boolean isInteger() {
        return true;
    }

     public boolean isCharacter() {
        return inner.isCharacter();
    }

}
