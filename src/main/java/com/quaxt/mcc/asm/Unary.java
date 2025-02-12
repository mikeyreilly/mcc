package com.quaxt.mcc.asm;

import com.quaxt.mcc.UnaryOperator;

import static com.quaxt.mcc.asm.TypeAsm.QUADWORD;

public record Unary(UnaryOperator op, TypeAsm type,
                    Operand operand) implements Instruction {
    @Override
    public String format(TypeAsm t) {
        return switch (op) {
            case IDIV -> "idiv";
            case NEGATE -> "not";
            case COMPLEMENT -> "neg";
            case NOT -> "not";
        } + (type == QUADWORD ? "q" : "l") + "\t";
    }
}
