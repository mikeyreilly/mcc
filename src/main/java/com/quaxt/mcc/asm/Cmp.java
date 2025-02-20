package com.quaxt.mcc.asm;

public record Cmp(TypeAsm type, Operand subtrahend, Operand minuend) implements Instruction {
    @Override
    public String format(TypeAsm t) {
        return switch(t) {
            case LONGWORD -> "cmpl	";
            case QUADWORD -> "cmpq	";
            case DOUBLE -> "comisd	";
        };
    }
}
