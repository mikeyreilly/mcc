package com.quaxt.mcc;

import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.CmpOperator.GREATER_THAN;
import static com.quaxt.mcc.semantic.Primitive.UINT;

public record UIntInit(int i) implements StaticInit, Constant<UIntInit> {
    @Override
    public Type type() {
        return UINT;
    }

    @Override
    public String toString() {
        return "ConstUInt[i=" + Integer.toUnsignedString(i) + "]";
    }

    @Override
    public boolean isZero() {
        return i == 0;
    }

    public long toLong() {
        return i;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, UIntInit v2) {
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
            case DIVIDE, DOUBLE_DIVIDE -> c = b == 0 ? 0 :Integer.divideUnsigned(a, b); //div by 0 is UB
            case REMAINDER -> c = b == 0 ? 0 : Integer.remainderUnsigned(a, b); //div by 0 is UB
            case AND, BITWISE_AND -> c = a & b;
            case OR, BITWISE_OR -> c = a | b;
            case BITWISE_XOR -> c = a ^ b;
            case SAR -> c = a >> b;
            case UNSIGNED_RIGHT_SHIFT -> c = a >>> b;
            case SHL -> c = a << b;
            case COMMA -> c = b;
            default -> {
                return null;
            }
        }
        return new UIntInit(c);
    }

    @Override
    public UIntInit apply(UnaryOperator op) {
        int a = i;
        int c;
        switch (op) {
            case BITWISE_NOT -> c = ~a;
            case UNARY_MINUS -> c = -a;
            case NOT -> c = a == 0 ? 1 : 0;
            case UNARY_SHR -> c = a >> 1;
            default -> {
                return null;
            }
        }
        return new UIntInit(c);
    }

    @Override
    public Constant<?> apply1(BinaryOperator op, Constant c2) {
        if (c2 instanceof UIntInit l) {
            return this.apply(op, l);
        }
        return this.apply(op, new UIntInit((int) c2.toLong()));
    }
}


