package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Dot(Exp structure, String member, Type type) implements Exp {}
