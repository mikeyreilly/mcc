package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.tacky.CharInit;
import com.quaxt.mcc.tacky.UCharInit;

public enum Primitive implements Type {
    CHAR(new CharInit(0), new CharInit((byte)0)), UCHAR(new UCharInit(0), new UCharInit(0)), SCHAR(new CharInit(0), new CharInit((byte)0)),
    INT(new IntInit(0), new IntInit(0)), UINT(new UIntInit(0), new UIntInit(0)), LONG(new LongInit(0), new LongInit(0L)),
    ULONG(new ULongInit(0), new ULongInit(0L)), DOUBLE(new DoubleInit(0), new DoubleInit(0d)),
    VOID(new UIntInit(0), new UIntInit(0)),;

    private final StaticInit zero;
    private final Constant zeroConstant;
    public final SingleInit zeroInitializer;

    Primitive(StaticInit zero, Constant zeroConstant) {
        this.zero = zero;
        this.zeroConstant = zeroConstant;
        this.zeroInitializer = new SingleInit(zeroConstant, this);
    }
    public StaticInit zero() {
        return zero;
    }

    @Override
    public boolean looseEquals(Type other) {
        return other==this;
    }

    public static Type fromTokenType(TokenType tokenType) {
        return switch (tokenType) {
            case DOUBLE_LITERAL -> DOUBLE;
            case UNSIGNED_LONG_LITERAL -> ULONG;
            case UNSIGNED_INT_LITERAL -> UINT;
            case LONG_LITERAL -> LONG;
            case INT_LITERAL -> INT;
            default -> null;
        };
    }
}
