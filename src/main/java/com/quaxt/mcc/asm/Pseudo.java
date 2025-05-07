package com.quaxt.mcc.asm;

public record Pseudo(String identifier) implements Reg {
    public Pseudo(String identifier){
        this.identifier=identifier;
    }
}
