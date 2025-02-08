package com.quaxt.mcc.semantic;

import com.quaxt.mcc.StaticInit;

public sealed interface Type permits FunType, Primitive {
    default StaticInit zero() {
        throw new RuntimeException(this + " has no zero");
    }
}
