package com.quaxt.mcc.asm;

public class Todo extends RuntimeException {
    public Todo(Object message) {
        super(String.valueOf(message));
    }

    public Todo() {
    }
}
