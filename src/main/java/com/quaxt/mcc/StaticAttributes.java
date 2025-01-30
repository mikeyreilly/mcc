package com.quaxt.mcc;

public record StaticAttributes(InitialValue init, boolean global) implements IdentifierAttributes {
    @Override
    public boolean defined() {
        return false;
    }
}
