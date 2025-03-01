package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Var(String name, Type type) implements Exp {
}
