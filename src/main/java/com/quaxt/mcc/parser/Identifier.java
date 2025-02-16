package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Identifier(String name, Type type) implements Exp {
    public Identifier(String name, Type type){
        this.name=name;
        this.type=type;
    }
}
