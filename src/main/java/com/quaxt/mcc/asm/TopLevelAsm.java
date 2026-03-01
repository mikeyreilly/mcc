package com.quaxt.mcc.asm;

public sealed interface TopLevelAsm permits DebugLineString,
        DebugString,
        FunctionAsm,
        StaticConstant,
        StaticVariableAsm {
}
