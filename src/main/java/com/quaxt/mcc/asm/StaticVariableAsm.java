package com.quaxt.mcc.asm;

public record StaticVariableAsm(String name, boolean global, int init) implements TopLevelAsm {
}
