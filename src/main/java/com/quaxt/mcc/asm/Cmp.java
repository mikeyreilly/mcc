package com.quaxt.mcc.asm;

import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;

public record Cmp(TypeAsm type, Operand subtrahend,
                  Operand minuend) implements Instruction {
    public Cmp(TypeAsm type, Operand subtrahend, Operand minuend) {
        this.type = type;
        this.subtrahend = subtrahend;
        this.minuend = minuend;
    }

    @Override
    public String format(TypeAsm t) {
        return switch (t) {
            case BYTE -> "cmpb	";
            case LONGWORD -> "cmpl	";
            case QUADWORD -> "cmpq	";
            case DOUBLE -> "comisd	";
            case ByteArray byteArray ->
                    throw new IllegalArgumentException("Can't handle " + byteArray);
        };
    }
}
