package com.quaxt.mcc.tacky;

import com.quaxt.mcc.asm.FunctionIr;

public sealed interface TopLevel permits FunctionIr, StaticConstant, StaticVariable {
}
