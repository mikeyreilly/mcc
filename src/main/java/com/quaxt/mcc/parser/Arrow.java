package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Arrow(Exp pointer, String member, Type type) implements Exp {}
