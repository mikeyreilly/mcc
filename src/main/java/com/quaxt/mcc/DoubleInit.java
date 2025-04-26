package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.ArithmeticOperator.SHR_TWO_OP;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.CmpOperator.GREATER_THAN;
import static com.quaxt.mcc.semantic.Primitive.DOUBLE;

public record DoubleInit(double d) implements StaticInit, Constant<DoubleInit> {
    @Override
    public Type type() {
        return DOUBLE;
    }

    public long toLong() {
        return (long)d;
    }

    @Override
    public boolean isZero() {
        return d == 0;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, DoubleInit v2) {
        double a = d;
        double b = v2.d;
        double c;
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
            default -> {
                return null;
            }
        }
        return new DoubleInit(c);
    }

    @Override
    public DoubleInit apply(UnaryOperator op) {
        double a = d;
        double c;
        switch (op) {
            case UNARY_MINUS -> c = -a;
            case NOT -> c = a == 0 ? 1 : 0;
            default -> {
                return null;
            }
        }
        return new DoubleInit(c);
    }
}
