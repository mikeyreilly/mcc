package com.quaxt.mcc.parser;
import com.quaxt.mcc.*;
import com.quaxt.mcc.tacky.ValIr;

sealed public interface Constant<C extends Constant<C>> extends Exp, ValIr permits
        BoolInit,
        CharInit,
        ConstantExp,
        DoubleInit,
        FloatInit,
        IntInit,
        LongInit,
        LongLongInit,
        ShortInit,
        UCharInit,
        UIntInit,
        ULongInit,
        ULongLongInit,
        UShortInit,
        Nullptr {
    long toLong();

    // try to apply op to these constants if it's supported
    Constant<?> apply(BinaryOperator op, C v2);

    default Constant<?> apply(BinaryOperator op, int v2) {
        Constant other = switch (this) {
            case BoolInit _ -> new BoolInit((byte) v2);
            case CharInit _ -> new BoolInit((byte) v2);
            case DoubleInit _ -> new DoubleInit(v2);
            case FloatInit _ -> new FloatInit(v2);
            case IntInit _ -> new IntInit(v2);
            case LongInit _ -> new LongInit(v2);
            case LongLongInit _ -> new LongLongInit(v2);
            case ShortInit _ -> new ShortInit((short) v2);
            case UCharInit _ -> new UCharInit((byte) v2);
            case UIntInit _ -> new UIntInit(v2);
            case ULongInit _ -> new ULongInit(v2);
            case ULongLongInit _ -> new ULongLongInit(v2);
            case UShortInit _ -> new UShortInit((short) v2);
            default -> throw new UnsupportedOperationException(
                    "can't apply " + op + " to " + this + " and int");
        };
        return this.apply(op, (C) other);
    }

    default Constant<?> apply(BinaryOperator op, long v2) {
        Constant other = switch (this) {
            case BoolInit _ -> new BoolInit((byte) v2);
            case CharInit _ -> new BoolInit((byte) v2);
            case DoubleInit _ -> new DoubleInit(v2);
            case FloatInit _ -> new FloatInit(v2);
            case IntInit _ -> new IntInit((int) v2);
            case LongInit _ -> new LongInit(v2);
            case LongLongInit _ -> new LongLongInit(v2);
            case ShortInit _ -> new ShortInit((short) v2);
            case UCharInit _ -> new UCharInit((byte) v2);
            case UIntInit _ -> new UIntInit((int) v2);
            case ULongInit _ -> new ULongInit(v2);
            case ULongLongInit _ -> new ULongLongInit(v2);
            case UShortInit _ -> new UShortInit((short) v2);
            default -> throw new UnsupportedOperationException(
                    "can't apply " + op + " to " + this + " and int");
        };
        return this.apply(op, (C) other);
    }

    boolean isZero();

   Constant apply(UnaryOperator op);

   default boolean isStatic() {
       return false;
   }

    Constant<?> apply1(BinaryOperator op, Constant c2);

    default boolean isFloatingPointType(){
        return false;
    }


}


