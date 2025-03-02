package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record AddrOf(Exp exp, Type type) implements Exp {
    public AddrOf(Exp exp, Type type) {
        this.exp = exp;
        this.type = type;
    }

    public Type type(){
        return type;
    }
}