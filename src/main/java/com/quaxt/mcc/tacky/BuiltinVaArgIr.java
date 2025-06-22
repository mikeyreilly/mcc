package com.quaxt.mcc.tacky;

import com.quaxt.mcc.semantic.Type;

public record BuiltinVaArgIr(VarIr retVal, VarIr dst, Type type) implements InstructionIr {}
