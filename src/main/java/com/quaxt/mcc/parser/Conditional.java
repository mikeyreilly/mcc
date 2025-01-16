package com.quaxt.mcc.parser;

public record Conditional(Exp condition, Exp ifTrue, Exp ifFalse) implements Exp {
}
