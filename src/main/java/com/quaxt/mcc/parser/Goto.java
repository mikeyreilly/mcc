package com.quaxt.mcc.parser;

public final class Goto implements Statement {
    public String label;

    public Goto(String label) {
        this.label = label;
    }
}
