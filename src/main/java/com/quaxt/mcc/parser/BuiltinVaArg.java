package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record BuiltinVaArg(Var identifier, Type type) implements Exp {}
