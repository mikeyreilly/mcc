package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Cast(Type type, Exp exp) implements Exp {
}