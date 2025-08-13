package com.quaxt.mcc.semantic;

import java.util.List;

public record FunType(List<Type> params, Type ret, boolean varargs) implements Type {

    @Override
    public boolean looseEquals(Type other) {
        return this.equals(other);
    }
}
