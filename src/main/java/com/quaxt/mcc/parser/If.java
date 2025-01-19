package com.quaxt.mcc.parser;

public record If(Exp condition, Statement ifTrue,
                 Statement ifFalse) implements Statement {
}
