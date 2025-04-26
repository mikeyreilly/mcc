package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.ValIr;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.ArithmeticOperator.SHR_TWO_OP;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.CmpOperator.GREATER_THAN;
import static com.quaxt.mcc.semantic.Primitive.CHAR;

public record CharInit(byte i) implements StaticInit, Constant<CharInit> {
    final static CharInit ZERO = new CharInit((byte) 0);

    public static ValIr zero() {
        return ZERO;
    }

    @Override
    public Type type() {
        return CHAR;
    }

    @Override
    public boolean isZero() {
        return i == 0;
    }
    @Override
    public String toString() {
        return String.valueOf(i);
    }


    public long toLong() {
        return i;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, CharInit v2) {
        byte a = i;
        byte b = v2.i;
        byte c;
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
            case SUB, DOUBLE_SUB -> c = (byte) (a - b);
            case ADD, DOUBLE_ADD -> c = (byte) (a + b);
            case IMUL, DOUBLE_MUL -> c = (byte) (a * b);
            case DIVIDE, DOUBLE_DIVIDE ->
                    c = (byte) (b == 0 ? 0 : a / b); // division by zero is UB
            case REMAINDER ->
                    c = (byte) (b == 0 ? 0 : a % b); // division by zero is UB
            case AND -> c = (byte) (a & b);
            case OR -> c = (byte) (a | b);
            case BITWISE_XOR -> c = (byte) (a ^ b);
            case SHL -> c = (byte) (a >> b);
            case SHR_TWO_OP -> c = (byte) (a << b);
            default -> {
                return null;
            }
        }
        return new CharInit(c);
    }

    @Override
    public CharInit apply(UnaryOperator op) {
        byte a = i;
        byte c;
        switch (op) {
            case BITWISE_NOT -> c = (byte) ~i;
            case UNARY_MINUS -> c = (byte) -a;
            case NOT -> c = (byte) (a == 0 ? 1 : 0);
            case SHR -> c = (byte) (a >> 1);
            default -> {
                return null;
            }
        }
        return new CharInit(c);
    }
}
