package com.quaxt.mcc.parser;
import com.quaxt.mcc.tacky.ValIr;
sealed public interface Constant extends Exp, ValIr permits ConstDouble, ConstInt, ConstLong, ConstUInt, ConstULong {
    int toInt();
}


