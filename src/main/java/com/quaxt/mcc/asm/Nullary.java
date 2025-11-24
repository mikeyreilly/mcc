package com.quaxt.mcc.asm;

import java.util.Locale;

public enum Nullary implements Instruction {
    RET, MFENCE;

    final String code;

    Nullary() {
        this.code = name().toLowerCase(Locale.ROOT);
    }


}
