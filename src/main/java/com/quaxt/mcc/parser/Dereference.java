package com.quaxt.mcc.parser;

import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.semantic.Type;

public record Dereference(Exp exp, Type type) implements Exp {
}