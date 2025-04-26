package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.semantic.Primitive.INT;

public record IntInit(int i) implements StaticInit, Constant<IntInit> {
    public static final IntInit ONE = new IntInit(1);
    public static final IntInit ZERO = new IntInit(0);

    @Override
    public Type type() {
        return INT;
    }

    public long toLong() {
        return i;
    }

    @Override
    public boolean isZero() {
        return i == 0;
    }

    @Override
    public IntInit apply(BinaryOperator op, IntInit v2) {
        int a = i;
        int b = v2.i;
        int c;
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
            case DIVIDE, DOUBLE_DIVIDE ->
                    c = b == 0 ? 0 : a / b; // division by zero is UB
            case REMAINDER -> c = b == 0 ? 0 : a % b; // division by zero is UB
            case AND -> c = a & b;
            case OR -> c = a | b;
            case BITWISE_XOR -> c = a ^ b;
            case SHL -> c = a >> b;
            case SHR_TWO_OP -> c = a << b;
            default -> {
                return null;
            }
        }
        return new IntInit(c);
    }

    @Override
    public IntInit apply(UnaryOperator op) {
        int a = i;
        int c;
        switch (op) {
            case BITWISE_NOT -> c = ~a;
            case UNARY_MINUS -> c = -a;
            case NOT -> c = a == 0 ? 1 : 0;
            case SHR -> c = a >> 1;
            default -> {
                return null;
            }
        }
        return new IntInit(c);
    }
}
