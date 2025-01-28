package com.quaxt.mcc.parser;

public record VarDecl(String name,
    Exp init, StorageClass storageClass) implements ForInit, Declaration {
}
