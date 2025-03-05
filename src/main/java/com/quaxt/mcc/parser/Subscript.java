package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Subscript(Exp array, Exp index, Type type) implements Exp {}
