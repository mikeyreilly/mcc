package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.ValIr;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.semantic.Primitive.CHAR;
import static com.quaxt.mcc.semantic.Primitive.SHORT;

public record ShortInit(short i) implements StaticInit, Constant<ShortInit> {
    final static ShortInit ZERO = new ShortInit((short) 0);

    public static ValIr zero() {
        return ZERO;
    }

    @Override
    public Type type() {
        return SHORT;
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
    public Constant<?> apply(BinaryOperator op, ShortInit v2) {
        short a = i;
        short b = v2.i;
        short c;
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
            case SUB, DOUBLE_SUB -> c = (short) (a - b);
            case ADD, DOUBLE_ADD -> c = (short) (a + b);
            case IMUL, DOUBLE_MUL -> c = (short) (a * b);
            case DIVIDE, DOUBLE_DIVIDE ->
                    c = (short) (b == 0 ? 0 : a / b); // division by zero is UB
            case REMAINDER ->
                    c = (short) (b == 0 ? 0 : a % b); // division by zero is UB
            case AND -> c = (short) (a & b);
            case OR -> c = (short) (a | b);
            case BITWISE_XOR -> c = (short) (a ^ b);
            case SAR -> c = (short) (a >> b);
            case SHL -> c = (short) (a << b);
            case COMMA -> c = b;
            default -> {
                return null;
            }
        }
        return new ShortInit(c);
    }

    @Override
    public ShortInit apply(UnaryOperator op) {
        short a = i;
        short c;
        switch (op) {
            case BITWISE_NOT -> c = (short) ~i;
            case UNARY_MINUS -> c = (short) -a;
            case NOT -> c = (short) (a == 0 ? 1 : 0);
            case UNARY_SHR -> c = (short) (a >> 1);
            default -> {
                return null;
            }
        }
        return new ShortInit(c);
    }

    @Override
    public Constant<?> apply1(BinaryOperator op, Constant c2) {
        if (c2 instanceof ShortInit l) {
            return this.apply(op, l);
        }
        return this.apply(op, new ShortInit((short)c2.toLong()));
    }
}
