package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;

public enum Primitive implements Type {
    INT(new IntInit(0)), UINT(new UIntInit(0)), LONG(new LongInit(0)),
    ULONG(new ULongInit(0)), DOUBLE(new DoubleInit(0));

    private final StaticInit zero;

    Primitive(StaticInit zero) {
        this.zero = zero;
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
