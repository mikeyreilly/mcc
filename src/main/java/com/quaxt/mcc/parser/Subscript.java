package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Subscript(Exp e1, Exp e2, Type type) implements Exp {
}
