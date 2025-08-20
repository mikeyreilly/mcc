package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.CharInit;
import com.quaxt.mcc.UCharInit;

public enum Primitive implements Type {
    CHAR(new CharInit((byte) 0)), UCHAR(new UCharInit((byte) 0)),
    SCHAR(new CharInit((byte) 0)), INT(IntInit.ZERO), UINT(new UIntInit(0)),
    SHORT(new ShortInit((short)0)), USHORT(new UShortInit((short)0)), LONG(new LongInit(0)),
    ULONG(new ULongInit(0)), DOUBLE(new DoubleInit(0)), FLOAT(new DoubleInit(0)), VOID(new UIntInit(0));

    private final StaticInit zero;
    public Initializer zeroInitializer;

    Primitive(StaticInit zero) {
        this.zero = zero;
        this.zeroInitializer = new SingleInit((Constant) zero, this);
    }

    public StaticInit zero() {
        return zero;
    }

    @Override
    public boolean looseEquals(Type other) {
        return other == this;
    }

    public static Type fromTokenType(TokenType tokenType) {
        return switch (tokenType) {
            case DOUBLE_LITERAL -> DOUBLE;
            case FLOAT_LITERAL -> FLOAT;
            case UNSIGNED_LONG_LITERAL -> ULONG;
            case HEX_INT_LITERAL -> INT;
            case UNSIGNED_HEX_LONG_LITERAL ->ULONG;
            case HEX_LONG_LITERAL -> LONG;
            case UNSIGNED_INT_LITERAL -> UINT;
            case LONG_LITERAL -> LONG;
            case INT_LITERAL -> INT;
            default -> null;
        };
    }
}
