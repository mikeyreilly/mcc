package com.quaxt.mcc.tacky;

public record StaticVariable(String name, boolean global, int init) implements TopLevel {
}
