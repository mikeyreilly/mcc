package com.quaxt.mcc.tacky;

public record VarIr(String identifier) implements ValIr {
    public VarIr(String identifier){
        this.identifier=identifier;
    }
}
