package com.quaxt.mcc.parser;
import com.quaxt.mcc.tacky.ValIr;
sealed public interface Constant extends Exp, ValIr permits ConstChar, ConstDouble, ConstInt, ConstLong, ConstUChar, ConstUInt, ConstULong {
    int toInt();
}


