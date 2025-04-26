package com.quaxt.mcc.parser;
import com.quaxt.mcc.*;
import com.quaxt.mcc.tacky.*;
import com.quaxt.mcc.tacky.ValIr;

sealed public interface Constant extends Exp, ValIr permits DoubleInit, IntInit, UIntInit, ULongInit, LongInit, CharInit, UCharInit {
    long toLong();
}


