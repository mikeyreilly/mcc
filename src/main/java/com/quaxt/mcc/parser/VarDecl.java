package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record VarDecl(String name,
                      Exp init, Type varType, StorageClass storageClass) implements ForInit, Declaration {
}
