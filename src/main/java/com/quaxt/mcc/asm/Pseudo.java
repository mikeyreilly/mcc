package com.quaxt.mcc.asm;

public record Pseudo(String identifier) implements Operand {
    public Pseudo(String identifier){
        this.identifier=identifier;
    }
}
