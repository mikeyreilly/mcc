package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;

import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;

public record Binary(ArithmeticOperator op, TypeAsm type, Operand src,
                     Operand dst) implements Instruction {
    public Binary(ArithmeticOperator op, TypeAsm type, Operand src,
                  Operand dst) {
        this.op = op;
        this.type = type;
        this.src = src;
        this.dst = dst;
    }

    @Override
    public String format(TypeAsm t) {
        return t == QUADWORD ? switch (op) {
            case SUB -> "subq	";
            case ADD -> "addq	";
            case IMUL -> "imulq	";
            case DIVIDE -> "divideq	";
            case REMAINDER -> "remainderq	";
            case AND, BITWISE_AND -> "andq	";
            case OR, BITWISE_OR -> "orq	";
            case BITWISE_XOR -> "xorq   ";
            case SHL -> "shlq	";
            case SAR -> "sarq	";
            case UNSIGNED_RIGHT_SHIFT -> "shrq  ";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        } : t == LONGWORD ? switch (op) {
            case SUB -> "subl	";
            case ADD -> "addl	";
            case IMUL -> "imull	";
            case DIVIDE -> "dividel	";
            case REMAINDER -> "remainderl	";
            case AND, BITWISE_AND -> "andl	";
            case OR, BITWISE_OR -> "orl	";
            case BITWISE_XOR -> "xorl   ";
            case DOUBLE_SUB -> "subsdl	";
            case DOUBLE_ADD -> "addsdl	";
            case DOUBLE_MUL -> "mulsdl	";
            case DOUBLE_DIVIDE -> "divl	";
            case SHL -> "shll	";
            case SAR -> "sarl	";
            case UNSIGNED_RIGHT_SHIFT -> "shrl  ";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        } : t == DOUBLE ? switch (op) {
            case DOUBLE_SUB -> "subsd	";
            case DOUBLE_ADD -> "addsd	";
            case DOUBLE_MUL -> "mulsd	";
            case DOUBLE_DIVIDE -> "divsd	";
            case BITWISE_XOR -> "xorpd	";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        } : switch (op) {
            case SHL -> "shlb	";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        };
    }
}
