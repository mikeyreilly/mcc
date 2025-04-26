package com.quaxt.mcc.tacky;
import com.quaxt.mcc.parser.Constant;

public sealed interface ValIr permits Constant, VarIr {
    boolean isStatic();
}
