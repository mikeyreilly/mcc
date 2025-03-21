package com.quaxt.mcc;

public sealed interface IdentifierAttributes permits ConstantAttr, FunAttributes, StaticAttributes, IdentifierAttributes.LocalAttr {
    default boolean defined() {
        return false;
    }

    default boolean global() {
        return false;
    }

    enum LocalAttr implements IdentifierAttributes {
        LOCAL_ATTR;
    }
}
