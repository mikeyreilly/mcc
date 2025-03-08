package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record VarDecl(Var name,
                      Initializer init, Type varType, StorageClass storageClass) implements ForInit, Declaration {
}
