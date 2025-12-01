package com.quaxt.mcc.asm;

import com.quaxt.mcc.UnaryOperator;

import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;

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
            case UNARY_SHR -> "shr";
            case BSWAP -> "bswap";
            default ->
                    throw new AssertionError("can't format " + op + " in assembly");
        } + switch (type) {
            case ByteArray byteArray ->
                    throw new AssertionError("unexpected operand " + op);
            case BYTE -> 'b';
            case WORD -> 'w';
            case LONGWORD -> 'l';
            case QUADWORD -> 'q';
            case FLOAT -> 's';
            case DOUBLE -> 'd';

        } + "\t";
    }
}
