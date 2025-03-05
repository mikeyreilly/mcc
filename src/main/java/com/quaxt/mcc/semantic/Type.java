package com.quaxt.mcc.semantic;

import com.quaxt.mcc.StaticInit;

import static com.quaxt.mcc.semantic.Primitive.*;
import static com.quaxt.mcc.semantic.Primitive.LONG;

public sealed interface Type permits FunType, Primitive, Pointer, Array {
    default StaticInit zero() {
        throw new RuntimeException(this + " has no zero");
    }

    default int size() {
        return switch (this) {
            case LONG, ULONG -> 8;
            case INT, UINT -> 4;
            default -> -1;
        };
    }

    default boolean unsignedOrDouble(){
        return this == UINT || this == ULONG | this == DOUBLE;
    }

    default boolean isSigned() {
        return this == INT || this == LONG || this == DOUBLE;
    }
}
