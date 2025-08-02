package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.semantic.Primitive.UCHAR;
import static com.quaxt.mcc.semantic.Primitive.USHORT;

public record UShortInit(short i) implements StaticInit, Constant<UShortInit> {
    @Override
    public Type type() {
        return USHORT;
    }

    public short i() {
        return i;
    }

    @Override
    public boolean isZero() {
        return i == 0;
    }

    @Override
    public String toString() {
        return "(ushort)" + (short) (i & 0xffff);
    }


    public long toLong() {
        return i;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, UShortInit v2) {
        int a = i & 0xffff;
        int b = v2.i & 0xffff;
        int c;
        switch (op) {
            case EQUALS -> {
                return a == b ? IntInit.ONE : IntInit.ZERO;
            }
            case NOT_EQUALS -> {
                return a != b ? IntInit.ONE : IntInit.ZERO;
            }
            case LESS_THAN_OR_EQUAL -> {
                return  Long.compareUnsigned(a, b) <= 0  ? IntInit.ONE : IntInit.ZERO;
            }
            case GREATER_THAN_OR_EQUAL -> {
                return Long.compareUnsigned(a, b) >= 0 ? IntInit.ONE : IntInit.ZERO;
            }
            case LESS_THAN -> {
                return Long.compareUnsigned(a, b) < 0  ? IntInit.ONE : IntInit.ZERO;
            }
            case GREATER_THAN -> {
                return Long.compareUnsigned(a, b) > 0  ? IntInit.ONE : IntInit.ZERO;
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
            case SAR -> c = a >> b;
            case SHL -> c = a << b;
            case UNSIGNED_RIGHT_SHIFT -> c = a >>> b;
            case COMMA -> c = b;
            default -> {
                return null;
            }
        }
        return new UShortInit((short) (c & 0xffff));
    }

    @Override
    public UShortInit apply(UnaryOperator op) {
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
        return new UShortInit(c);
    }

    @Override
    public Constant<?> apply1(BinaryOperator op, Constant c2) {
        if (c2 instanceof UShortInit l) {
            return this.apply(op, l);
        }
        return this.apply(op, new UShortInit((short)c2.toLong()));
    }
}
