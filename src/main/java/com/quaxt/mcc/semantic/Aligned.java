package com.quaxt.mcc.semantic;

import com.quaxt.mcc.parser.Constant;

public record Aligned(Type inner, int alignment) implements Type {

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
