package com.quaxt.mcc.parser;

public record If(Exp condition, Statement ifTrue,
                 Statement ifFalse) implements Statement {
    public If(Exp condition, Statement ifTrue,
              Statement ifFalse) {
        this.condition = condition;
        this.ifTrue = ifTrue;
        this.ifFalse = ifFalse;
    }
}
