package com.quaxt.mcc;

import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.CmpOperator.GREATER_THAN;
import static com.quaxt.mcc.semantic.Primitive.BOOL;
import static com.quaxt.mcc.semantic.Primitive.UCHAR;

public record BoolInit(byte i) implements StaticInit, Constant<BoolInit> {
    public static final BoolInit TRUE = new BoolInit((byte) 1);
    public static final BoolInit FALSE = new BoolInit((byte) 0);

    @Override
    public Type type() {
        return BOOL;
    }

    public byte i() {
        return i;
    }

    @Override
    public boolean isZero() {
        return i == 0;
    }

    @Override
    public String toString() {
        return i == 0 ? "false" :
                i == 1 ? "true" : "(bool)" + (char) (i & 0xff);
    }

    public long toLong() {
        return i;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, BoolInit v2) {
        int a = i & 0xff;
        int b = v2.i & 0xff;
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
            case SHR -> c = a >>> b;
            case COMMA -> c = b;
            default -> {
                return null;
            }
        }
        return new BoolInit((byte) (c & 0xff));
    }

    @Override
    public BoolInit apply(UnaryOperator op) {
        byte a = i;
        byte c;
        switch (op) {
            case BITWISE_NOT -> c = (byte) ~i;
            case UNARY_MINUS -> c = (byte) -a;
            case NOT -> c = (byte) (a == 0 ? 1 : 0);
            case UNARY_SHR -> c = (byte) (a >> 1);
            default -> {
                return null;
            }
        }
        return new BoolInit(c);
    }

    @Override
    public Constant<?> apply1(BinaryOperator op, Constant c2) {
        if (c2 instanceof BoolInit l) {
            return this.apply(op, l);
        }
        return this.apply(op, new BoolInit((byte)c2.toLong()));
    }
}
