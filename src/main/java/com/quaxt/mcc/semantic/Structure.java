package com.quaxt.mcc.semantic;

import com.quaxt.mcc.asm.Todo;

public record Structure(String tag) implements Type {
    public boolean looseEquals(Type other) {
        throw new Todo();
    }
}
