package com.quaxt.mcc.asm;

import com.quaxt.mcc.tacky.InstructionIr;

import java.util.Locale;

public enum Nullary implements Instruction, InstructionIr {
    RET, MFENCE;

    final String code;

    Nullary() {
        this.code = name().toLowerCase(Locale.ROOT);
    }


    @Override
    public String format(TypeAsm t) {
        return Instruction.super.format(t);
    }
}
