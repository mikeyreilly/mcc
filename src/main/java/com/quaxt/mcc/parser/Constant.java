package com.quaxt.mcc.parser;
import com.quaxt.mcc.*;
import com.quaxt.mcc.tacky.ValIr;

sealed public interface Constant<C extends Constant<C>> extends Exp, ValIr permits DoubleInit, IntInit, UIntInit, ULongInit, LongInit, CharInit, UCharInit {
    long toLong();

    // try to apply op to these constants if it's supported
    Constant<?> apply(BinaryOperator op, C v2);

    boolean isZero();

   Constant apply(UnaryOperator op);

   default boolean isStatic() {
       return false;
   }

    Constant<?> apply1(BinaryOperator op, Constant c2);

}


