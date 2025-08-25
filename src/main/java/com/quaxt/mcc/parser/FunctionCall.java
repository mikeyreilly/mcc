package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import java.util.List;

public record FunctionCall(Exp name, List<Exp> args, boolean varargs, Type type) implements Exp {
    public FunctionCall(Exp name, List<Exp> args, boolean varargs, Type type){
        this.name=name;
        this.args=args;
        this.varargs=varargs;
        this.type=type;
    }
}
