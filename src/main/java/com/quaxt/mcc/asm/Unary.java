package com.quaxt.mcc.asm;

import com.quaxt.mcc.UnaryOperator;

import static com.quaxt.mcc.asm.TypeAsm.QUADWORD;

public record Unary(UnaryOperator op, TypeAsm type,
                    Operand operand) implements Instruction {
    @Override
    public String format(TypeAsm t) {
        return switch (op) {
            case DIV -> "div";
            case IDIV -> "idiv";
            case BITWISE_NOT -> "not";
            case UNARY_MINUS -> "neg";
            case NOT -> "not";
            case SHR -> "shr";
        } + (type == QUADWORD ? "q" : "l") + "\t";
    }
}
