package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.semantic.Primitive.LONGLONG;

public record LongLongInit(long l) implements StaticInit, Constant<LongLongInit> {
    @Override
    public Type type() {
        return LONGLONG;
    }


    public long toLong() {
        return l;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, LongLongInit v2) {
        long a = l;
        long b = v2.l;
        long c;
        switch (op) {
            case EQUALS -> {
                return a == b ? IntInit.ONE : IntInit.ZERO;
            }
            case NOT_EQUALS -> {
                return a != b ? IntInit.ONE : IntInit.ZERO;
            }
            case LESS_THAN_OR_EQUAL -> {
                return a <= b ? IntInit.ONE : IntInit.ZERO;
            }
            case GREATER_THAN_OR_EQUAL -> {
                return a >= b ? IntInit.ONE : IntInit.ZERO;
            }
            case LESS_THAN -> {
                return a < b ? IntInit.ONE : IntInit.ZERO;
            }
            case GREATER_THAN -> {
                return a > b ? IntInit.ONE : IntInit.ZERO;
            }
            case SUB, DOUBLE_SUB -> c = a - b;
            case ADD, DOUBLE_ADD -> c = a + b;
            case IMUL, DOUBLE_MUL -> c = a * b;
            case DIVIDE, DOUBLE_DIVIDE -> c = a / b;
            case REMAINDER -> c = a % b;
            case AND, BITWISE_AND -> c = a & b;
            case OR, BITWISE_OR -> c = a | b;
            case BITWISE_XOR -> c = a ^ b;
            case SAR -> c = a >> b;
            case SHL -> c = a << b;
            case COMMA -> c = b;
            default -> {
                return null;
            }
        }
        return new LongLongInit(c);
    }

    @Override
    public boolean isZero() {
        return l == 0;
    }

    @Override
    public LongLongInit apply(UnaryOperator op) {
        long a = l;
        long c;
        switch (op) {
            case BITWISE_NOT -> c = ~l;
            case UNARY_MINUS -> c = -a;
            case NOT -> c = a == 0 ? 1 : 0;
            case UNARY_SHR -> c = a >> 1;
            default -> {
                return null;
            }
        }
        return new LongLongInit(c);
    }

    @Override
    public Constant<?> apply1(BinaryOperator op, Constant c2) {
        if (c2 instanceof LongLongInit l) {
            return this.apply(op, l);
        }
        return this.apply(op, new LongLongInit(c2.toLong()));
    }


}
