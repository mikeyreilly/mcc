package com.quaxt.mcc.asm;

public record ObjEntry(TypeAsm type, boolean isStatic, boolean isConstant) implements SymTabEntryAsm {
}
