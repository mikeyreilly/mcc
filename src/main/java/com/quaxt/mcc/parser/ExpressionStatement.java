package com.quaxt.mcc.parser;

import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.semantic.Primitive;
import com.quaxt.mcc.semantic.Type;

public record ExpressionStatement(Block stmt) implements Exp {
    @Override
    public Type type() {
        BlockItem x= stmt.blockItems().getLast();
        return switch (x) {
            case Exp e -> e.type();
            case If _-> Primitive.VOID;
            default -> {
                throw new Todo();
            }
        };
    }
}
