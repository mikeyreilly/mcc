package com.quaxt.mcc.asm;

import java.io.PrintWriter;
import java.util.Locale;

public enum Nullary implements Instruction {
    CDQ,RET;

    final String code;

    Nullary() {
        this.code = name().toLowerCase(Locale.ROOT);
    }


}
