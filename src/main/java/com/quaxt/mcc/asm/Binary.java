package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;

import static com.quaxt.mcc.asm.TypeAsm.QUADWORD;

public record Binary(ArithmeticOperator op, TypeAsm type, Operand src, Operand dst) implements Instruction {
    @Override
    public String format(TypeAsm t) {
        return switch (op) {
            case SUB, ADD,
                 IMUL,
                 DIVIDE,
                 REMAINDER,
                 AND,
                 OR -> op.toString().toLowerCase();
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        } + (type == QUADWORD ? "q" : "l") + "\t";
    }
}
