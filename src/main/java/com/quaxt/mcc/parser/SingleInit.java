package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record SingleInit(Exp exp, Type type) implements Initializer {
    public SingleInit(Exp exp, Type type) {
        this.exp = exp;
        this.type = type;
    }
}
