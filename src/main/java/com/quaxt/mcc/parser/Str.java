package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Str(String s, Type type) implements Exp {}
