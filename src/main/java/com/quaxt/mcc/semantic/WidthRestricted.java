package com.quaxt.mcc.semantic;

import static com.quaxt.mcc.semantic.Primitive.*;

/** used to keep track of values coming from bit-fields. May be refactored someday to support BitInts.*/
public record WidthRestricted(Type integerType, int width) implements Type {

    @Override
    public boolean looseEquals(Type other) {
        return this.equals(other);
    }

     public boolean isSigned() {
        return integerType.isSigned();
    }

     public boolean isInteger() {
        return true;
    }

     public boolean isCharacter() {
        return integerType.isCharacter();
    }

}
