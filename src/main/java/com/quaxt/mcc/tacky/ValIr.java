package com.quaxt.mcc.tacky;
import com.quaxt.mcc.AbstractValue;
import com.quaxt.mcc.parser.Constant;

public sealed interface ValIr extends AbstractValue permits Constant, VarIr {
    boolean isStatic();
}
