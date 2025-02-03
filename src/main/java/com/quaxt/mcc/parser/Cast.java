package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Cast(Type targetType, Exp exp) implements Exp {
}