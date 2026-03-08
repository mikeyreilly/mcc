package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.CharInit;
import com.quaxt.mcc.UCharInit;
import com.quaxt.mcc.tacky.ValIr;

public enum Primitive implements Type {
    CHAR(new CharInit((byte) 0), "char"),
    UCHAR(new UCharInit((byte) 0), "unsigned char"),
    SCHAR(new CharInit((byte) 0), "signed char"),
    INT(IntInit.ZERO, "int"),
    UINT(new UIntInit(0), "unsigned int"),
    SHORT(new ShortInit((short)0), "short"),
    USHORT(new UShortInit((short)0), "unsigned short"),
    LONG(new LongInit(0), "long"),
    ULONG(new ULongInit(0), "unsigned long"),
    LONGLONG(new LongLongInit(0), "long long"),
    ULONGLONG(new ULongLongInit(0), "unsigned long long"),
    DOUBLE(new DoubleInit(0), "double"),
    FLOAT(new DoubleInit(0), "float"),
    VOID(new UIntInit(0), "unsigned int"),
    BOOL(new BoolInit((byte)0), "bool");

    private final Constant<?> zero;
    public Initializer zeroInitializer;
    public final String name;

    Primitive(Constant<?> zero, String name) {
        this.zero = zero;
        this.zeroInitializer = new SingleInit((Constant) zero, this);
        this.name = name;
    }

    public Constant<?> zero() {
        return zero;
    }

    @Override
    public boolean looseEquals(Type other) {
        return other == this;
    }

    @Override
    public ValIr fromLong(long l) {
        return switch (this) {
            case CHAR -> new CharInit((byte) l);
            case UCHAR -> new CharInit((byte) l);
            case SCHAR -> new CharInit((byte) l);
            case INT -> new IntInit((int) l);
            case UINT -> new UIntInit((int) l);
            case SHORT -> new UShortInit((short) l);
            case USHORT -> new ShortInit((short) l);
            case LONG -> new LongInit(l);
            case ULONG -> new ULongInit(l);
            case LONGLONG -> new LongLongInit(l);
            case ULONGLONG -> new ULongLongInit( l);
            case DOUBLE -> new DoubleInit((double) l);
            case FLOAT -> new FloatInit((float) l);
            case VOID -> throw new UnsupportedOperationException("Can't create void value from long");
            case BOOL -> new BoolInit((byte) l);
        };
    }

    public static Type fromTokenType(TokenType tokenType) {
        return switch (tokenType) {
            case HEX_DOUBLE_LITERAL, DOUBLE_LITERAL -> DOUBLE;
            case HEX_FLOAT_LITERAL, FLOAT_LITERAL -> FLOAT;
            case UNSIGNED_LONG_LITERAL -> ULONG;
            case HEX_INT_LITERAL -> INT;
            case UNSIGNED_HEX_INT_LITERAL ->UINT;
            case UNSIGNED_HEX_LONG_LITERAL ->ULONG;
            case HEX_LONG_LITERAL -> LONG;
            case UNSIGNED_INT_LITERAL -> UINT;
            case LONG_LITERAL -> LONG;
            case INT_LITERAL -> INT;
            default -> null;
        };
    }
}
