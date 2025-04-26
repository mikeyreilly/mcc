package com.quaxt.mcc.asm;

import com.quaxt.mcc.Err;

public sealed interface Operand permits Data, DoubleReg, Imm, Pseudo, Reg, Memory, PseudoMem, Indexed {
    default Operand plus(long offset) {
        throw new Err("Operation not supported");
    }
}
