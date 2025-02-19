package com.quaxt.mcc.asm;

import com.quaxt.mcc.DoubleInit;

public record StaticConstant(String label, int alignment, DoubleInit init) implements TopLevelAsm {
}
