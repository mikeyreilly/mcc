package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.ArithmeticOperator.SHR_TWO_OP;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.CmpOperator.GREATER_THAN;
import static com.quaxt.mcc.semantic.Primitive.ULONG;

public record ULongInit(long l) implements StaticInit, Constant<ULongInit> {

    @Override
    public Type type() {
        return ULONG;
    }

    @Override
    public String toString() {
        return "ConstULong[l=" + Long.toUnsignedString(l) + "]";
    }

    @Override
    public boolean isZero() {
        return l == 0;
    }

    public long toLong() {
        return l;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, ULongInit v2) {
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
            case DIVIDE, DOUBLE_DIVIDE ->  c = b == 0 ? 0 : Long.divideUnsigned(a, b); // division by zero is UB
            case REMAINDER -> c = b == 0 ? 0 : Long.remainderUnsigned(a, b); // division by zero is UB
            case AND -> c = a & b;
            case OR -> c = a | b;
            case BITWISE_XOR -> c = a ^ b;
            case SHL -> c = a >> b;
            case SHR_TWO_OP -> c = a << b;
            default -> {
                return null;
            }
        }
        return new ULongInit(c);
    }

    @Override
    public ULongInit apply(UnaryOperator op) {
        long a = l;
        long c;
        switch (op) {
            case BITWISE_NOT -> c = ~a;
            case UNARY_MINUS -> c = -a;
            case NOT -> c = a == 0 ? 1 : 0;
            case SHR -> c = a >> 1;
            default -> {
                return null;
            }
        }
        return new ULongInit(c);
    }
}
