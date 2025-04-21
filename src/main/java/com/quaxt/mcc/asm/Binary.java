package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;

import static com.quaxt.mcc.asm.PrimitiveTypeAsm.LONGWORD;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.QUADWORD;

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
            case SHL -> "shl	";
            case SHR_TWO_OP -> "shr	";
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
            case DOUBLE_DIVIDE -> "divsd	";
            case BITWISE_XOR -> "xorpd	";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        };
    }
}
