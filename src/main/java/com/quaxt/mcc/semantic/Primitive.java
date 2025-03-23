package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.tacky.CharInit;
import com.quaxt.mcc.tacky.UCharInit;

public enum Primitive implements Type {
    CHAR(new CharInit(0), new ConstChar((byte)0)), UCHAR(new UCharInit(0), new ConstUChar(0)), SCHAR(new CharInit(0), new ConstChar((byte)0)),
    INT(new IntInit(0), new ConstInt(0)), UINT(new UIntInit(0), new ConstUInt(0)), LONG(new LongInit(0), new ConstLong(0L)),
    ULONG(new ULongInit(0), new ConstULong(0L)), DOUBLE(new DoubleInit(0), new ConstDouble(0d));

    private final StaticInit zero;
    private final Constant zeroConstant;
    public final SingleInit zeroInitializer;

    Primitive(StaticInit zero, Constant zeroConstant) {
        this.zero = zero;
        this.zeroConstant = zeroConstant;
        this.zeroInitializer = new SingleInit(zeroConstant);
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
