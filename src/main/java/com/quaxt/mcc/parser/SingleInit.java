package com.quaxt.mcc.parser;

public record SingleInit(Exp exp) implements Initializer {
    public SingleInit(Exp exp){
        this.exp=exp;
    }
}
