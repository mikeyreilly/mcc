package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.ValIr;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.semantic.Primitive.DOUBLE;
import static com.quaxt.mcc.semantic.Primitive.FLOAT;

public record FloatInit(float d) implements StaticInit, Constant<FloatInit> {
    public static final ValIr ONE = new FloatInit(1f);

    @Override
    public Type type() {
        return FLOAT;
    }

    public long toLong() {
        return (long)d;
    }

    @Override
    public boolean isZero() {
        return d == 0;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, FloatInit v2) {
        float a = d;
        float b = v2.d;
        float c;
        switch (op) {
            case EQUALS -> {
                return a == b ? IntInit.ONE : IntInit.ZERO;
            }
            case NOT_EQUALS -> {
                return a != b ? IntInit.ONE : IntInit.ZERO;
            }
            case LESS_THAN_OR_EQUAL -> {
                return  a <= b ? IntInit.ONE : IntInit.ZERO;
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
            case COMMA -> c = b;
            default -> {
                return null;
            }
        }
        return new FloatInit(c);
    }

    @Override
    public FloatInit apply(UnaryOperator op) {
        float a = d;
        float c;
        switch (op) {
            case UNARY_MINUS -> c = -a;
            case NOT -> c = a == 0 ? 1 : 0;
            default -> {
                return null;
            }
        }
        return new FloatInit(c);
    }

    @Override
    public Constant<?> apply1(BinaryOperator op, Constant c2) {
        if (c2 instanceof FloatInit l) {
            return this.apply(op, l);
        }
        return this.apply(op, new FloatInit((float)c2.toLong()));
    }
}
