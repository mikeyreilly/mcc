package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record VarDecl(Var name, Initializer init, Type varType,
                      StorageClass storageClass,
                      StructOrUnionSpecifier structOrUnionSpecifier) implements Declaration {
    public VarDecl withType(Type varType) {
        if (varType == this.varType) return this;
        return new VarDecl(name, init, varType, storageClass,
                structOrUnionSpecifier);
    }
}
