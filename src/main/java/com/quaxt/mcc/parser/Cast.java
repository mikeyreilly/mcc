package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Cast(Type type, Exp exp) implements Exp {
    public Cast(Type type, Exp exp){
        this.type=type;
        this.exp=exp;
        System.out.println(this);
    }
}