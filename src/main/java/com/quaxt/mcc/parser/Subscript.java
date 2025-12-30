package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Subscript(Exp e1, Exp e2, Type type) implements Exp {
    public Subscript(Exp e1, Exp e2, Type type){
        this.e1=e1;
        this.e2=e2;
        this.type=type;
    }
}
