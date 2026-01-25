package com.quaxt.mcc.semantic;

import com.quaxt.mcc.parser.Exp;

import java.util.List;

public record FunType(List<Type> params, Type ret, boolean varargs, Exp alignment) implements Type {

    @Override
    public boolean looseEquals(Type other) {
        return this.equals(other);
    }

    public FunType withAlignment(Exp alignment) {
        return new FunType(params, ret, varargs, alignment);
    }
}
