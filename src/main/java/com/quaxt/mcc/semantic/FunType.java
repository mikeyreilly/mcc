package com.quaxt.mcc.semantic;

import java.util.List;

public record FunType(List<Type> params, Type ret) implements Type {
    public FunType(List<Type> params, Type ret) {
        this.params = params;
        this.ret = ret;
    }

    @Override
    public boolean looseEquals(Type other) {
        return other.equals(this);
    }
}
