package com.quaxt.mcc.asm;

import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.JumpIfNotZero;
import com.quaxt.mcc.tacky.JumpIfZero;
import com.quaxt.mcc.tacky.LabelIr;

import static com.quaxt.mcc.asm.PrimitiveTypeAsm.DOUBLE;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.QUADWORD;

public sealed interface Instruction permits Binary, Call, Cdq, Cmp, Cvtsi2sd, Cvttsd2si, JmpCC, Lea, Mov, MovZeroExtend, Movsx, Nullary, Push, SetCC, Unary, Jump, LabelIr {
    default String format(TypeAsm t) {
        return this.getClass().getSimpleName().toLowerCase() +
            (t == QUADWORD ? "q" : t == DOUBLE ? "sd" : "l") + "\t";
    }

}
