package com.quaxt.mcc.tacky;

import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.StaticAttributes;

public record VarIr(String identifier) implements ValIr {
    public VarIr(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public boolean isStatic() {
        return Mcc.SYMBOL_TABLE.get(identifier).attrs() instanceof StaticAttributes;
    }
}
