package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Conditional(Exp condition, Exp ifTrue, Exp ifFalse, Type type) implements Exp {
}
