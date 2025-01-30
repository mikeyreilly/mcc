package com.quaxt.mcc;

public sealed interface IdentifierAttributes permits FunAttributes, StaticAttributes, IdentifierAttributes.LocalAttr {
    boolean defined();

    boolean global();

    enum LocalAttr implements IdentifierAttributes {
        LOCAL_ATTR;

        @Override
        public boolean defined() {
            return false;
        }

        @Override
        public boolean global() {
            return false;
        }
    }
}
