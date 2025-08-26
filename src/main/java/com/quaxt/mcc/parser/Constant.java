package com.quaxt.mcc.parser;
import com.quaxt.mcc.*;
import com.quaxt.mcc.tacky.ValIr;

sealed public interface Constant<C extends Constant<C>> extends Exp, ValIr permits CharInit, ConstantExp, DoubleInit, FloatInit, IntInit, LongInit, ShortInit, UCharInit, UIntInit, ULongInit, UShortInit {
    long toLong();

    // try to apply op to these constants if it'declarationSpecifiers supported
    Constant<?> apply(BinaryOperator op, C v2);

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


