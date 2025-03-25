package com.quaxt.mcc.asm;

import com.quaxt.mcc.StaticInit;

public record StaticConstant(String label, int alignment,
                             StaticInit init) implements TopLevelAsm {

}
