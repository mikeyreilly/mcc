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
            case SHR -> "shrq  ";
            case BSR -> "bsrq\t";
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
            case SHR -> "shrl  ";
            case BSR -> "bsrl\t";
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
        } : t == FLOAT ? switch (op) {
            case DOUBLE_SUB -> "subss	";
            case DOUBLE_ADD -> "addss	";
            case DOUBLE_MUL -> "mulss	";
            case DOUBLE_DIVIDE -> "divss	";
            case BITWISE_XOR -> "xorps	";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        } : t == WORD ? switch (op) {
            case SUB -> "subw	";
            case ADD -> "addw	";
            case IMUL -> "imulw	";
            case DIVIDE -> "dividew	";
            case REMAINDER -> "remainderw	";
            case AND, BITWISE_AND -> "andw	";
            case OR, BITWISE_OR -> "orw	";
            case BITWISE_XOR -> "xorw   ";
            case SHL -> "shlw	";
            case SAR -> "sarw	";
            case SHR -> "shrw  ";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        } : switch (op) {
            case SUB -> "subb	";
            case ADD -> "addb	";
            case IMUL -> "imulb	";
            case DIVIDE -> "divideb	";
            case REMAINDER -> "remainderb	";
            case AND, BITWISE_AND -> "andb	";
            case OR, BITWISE_OR -> "orb	";
            case BITWISE_XOR -> "xorb   ";
            case SHL -> "shlb	";
            case SAR -> "sarb	";
            case SHR -> "shrb  ";
            default ->
                    throw new IllegalStateException("Unexpected value: " + op);
        };
    }
}
