package com.quaxt.mcc.asm;

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
            case LONGWORD -> "cmpl	";
            case QUADWORD -> "cmpq	";
            case DOUBLE -> "comisd	";
        };
    }
}
