package com.quaxt.mcc.asm;

import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;

public record Test(TypeAsm type, Operand src1,
                   Operand src2) implements Instruction {

    @Override
    public String format(TypeAsm t) {
        switch (t) {
            case BYTE:
                return "testb	";
            case LONGWORD:
                return "testl	";
            case QUADWORD:
                return "testq	";
            default:
                throw new IllegalArgumentException("Can't handle "+t);
        }
    }
}
