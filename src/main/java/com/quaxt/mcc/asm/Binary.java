package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;

import static com.quaxt.mcc.asm.TypeAsm.LONGWORD;
import static com.quaxt.mcc.asm.TypeAsm.QUADWORD;

public record Binary(ArithmeticOperator op, TypeAsm type, Operand src,
                     Operand dst) implements Instruction {
    @Override
    public String format(TypeAsm t) {
        return t == QUADWORD ? switch (op) {
            case SUB -> "subq	";
            case ADD -> "addq	";
            case IMUL -> "imulq	";
            case DIVIDE -> "divideq	";
            case REMAINDER -> "remainderq	";
            case AND -> "andq	";
            case OR -> "orq	";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        } : t == LONGWORD ?
                switch (op) {
                    case SUB -> "subl	";
                    case ADD -> "addl	";
                    case IMUL -> "imull	";
                    case DIVIDE -> "dividel	";
                    case REMAINDER -> "remainderl	";
                    case AND -> "andl	";
                    case OR -> "orl	";
                    case DOUBLE_SUB -> "subsdl	";
                    case DOUBLE_ADD -> "addsdl	";
                    case DOUBLE_MUL -> "mulsdl	";
                    case DOUBLE_DIVIDE -> "divl	";
                    default ->
                            throw new IllegalStateException("Unexpected value: " + op);
                } : switch (op) {
            case DOUBLE_SUB -> "subsd	";
            case DOUBLE_ADD -> "addsd	";
            case DOUBLE_MUL -> "mulsd	";
            case DOUBLE_DIVIDE -> "div	";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        };
    }
}
