package com.quaxt.mcc.asm;

import com.quaxt.mcc.AbstractValue;
import com.quaxt.mcc.Err;

public sealed interface Operand extends AbstractValue permits Data, Imm, Indexed, LabelAddress, Memory, PseudoMem, Reg {
    default Operand plus(long offset) {
        if (offset==0) return this;
        throw new Err("Operation not supported");
    }
}
