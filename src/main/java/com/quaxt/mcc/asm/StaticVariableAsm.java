package com.quaxt.mcc.asm;

import com.quaxt.mcc.StaticInit;

import java.util.List;

public record StaticVariableAsm(String name, boolean global, int alignment,
                                List<StaticInit> init) implements TopLevelAsm {
}
