package com.quaxt.mcc.semantic;

import com.quaxt.mcc.Err;
import com.quaxt.mcc.StaticInit;
import com.quaxt.mcc.parser.Constant;

import static com.quaxt.mcc.semantic.Primitive.*;
import static com.quaxt.mcc.semantic.Primitive.LONG;

public sealed interface Type permits FunType, Primitive, Pointer, Array {
    default StaticInit zero() {
        throw new Err(this + " has no zero");
    }

    default int size() {
        return switch (this) {
            case LONG, ULONG -> 8;
            case INT, UINT -> 4;
            default -> -1;
        };
    }

    default boolean unsignedOrDouble() {
        return this == UINT || this == ULONG | this == DOUBLE;
    }

    default boolean isSigned() {
        return this == INT || this == LONG || this == DOUBLE;
    }

    default boolean isInteger() {
        return this == INT || this == LONG || this == UINT || this == ULONG;
    }

    /**
     * We just support 2^31 int length integers but the parser accepts other
     * things e.g. unsigned longs. looseEquals compares types so that if they're
     * arrays and they have the same type and length (even though the lengths
     * are of different type then return true.
     */
    default boolean looseEquals(Type other) {
        return other.equals(this)
            || this instanceof Array(Type element1, Constant arraySize1)
               && other instanceof Array(Type element2, Constant arraySize2)
               && arraySize1.toInt() == arraySize2.toInt() && element1.looseEquals(element2)

            || this instanceof Pointer(Type element1)
               && other instanceof Pointer(Type element2)
               && element1.looseEquals(element2);

    }
}
