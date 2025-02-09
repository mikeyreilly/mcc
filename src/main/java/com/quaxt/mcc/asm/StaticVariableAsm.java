package com.quaxt.mcc.asm;

import com.quaxt.mcc.StaticInit;

public record StaticVariableAsm(String name, boolean global, int alignment, StaticInit init) implements TopLevelAsm {
}
