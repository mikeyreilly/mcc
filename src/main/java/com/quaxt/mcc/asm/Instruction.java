package com.quaxt.mcc.asm;

import com.quaxt.mcc.AbstractInstruction;
import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.LabelIr;

import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;

public sealed interface Instruction extends AbstractInstruction permits Binary, Call, Cdq, Cmp, Comment, Cvtsi2sd, Cvttsd2si, JmpCC, Lea, Mov, MovZeroExtend, Movsx, Nullary, Pop, Push, SetCC, Test, Unary, Jump, LabelIr {
    default String format(TypeAsm t) {
        return this.getClass().getSimpleName().toLowerCase() + switch (t) {
            case ByteArray byteArray -> "q";
            case BYTE -> "b";
            case WORD -> "w";
            case LONGWORD -> "l";
            case QUADWORD -> "q";
            case DOUBLE -> "sd";
        } + "\t";
    }

}
