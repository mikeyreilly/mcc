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
            case LONG, ULONG, DOUBLE -> 8;
            case INT, UINT -> 4;
            case CHAR, UCHAR, SCHAR -> 1;
            case Array(Type element, Constant arraySize) ->
                    element.size() * arraySize.toInt();
            case Pointer _ -> 8;
            default ->
                    throw new AssertionError("don't know the size in this case");
        };
    }

    default boolean unsignedOrDoubleOrPointer() {
        return this == UINT || this == ULONG || this == UCHAR | this == DOUBLE || this instanceof Pointer;
    }

    default boolean isSigned() {
        return this == INT || this == LONG || this == DOUBLE || this == CHAR || this == SCHAR;
    }

    default boolean isInteger() {
        return this.isCharacter() || this == INT || this == LONG || this == UINT || this == ULONG || this == CHAR || this == SCHAR || this == UCHAR;
    }

    default boolean isCharacter() {
        return this == CHAR || this == UCHAR || this == SCHAR;
    }

    /**
     * We just support 2^31 int length integers but the parser accepts other
     * things e.g. unsigned longs. looseEquals compares types so that if they're
     * arrays and they have the same type and length (even though the lengths
     * are of different type then return true.
     */
    boolean looseEquals(Type other);

    default boolean isScalar() {
        return this.isInteger() || this instanceof Pointer;
    }
}
