package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import java.util.ArrayList;

public record Generic(Exp controllingExp, ArrayList<Cast> genericAssocList,
                      Exp defaultExp) implements Exp {
    @Override
    public Type type() {
        return null;
    }
}
