package com.quaxt.mcc.parser;

public record VarDecl(String name,
    Exp init) implements ForInit, Declaration {
}
