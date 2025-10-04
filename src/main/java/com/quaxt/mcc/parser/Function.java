package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.FunType;

import java.util.List;

public final class Function implements Declaration {
    public String name;
    public List<Var> parameters;
    public Block body;
    public FunType funType;
    public StorageClass storageClass;
    public boolean callsVaStart;
    public boolean usesFunc;

    public Function(String name, List<Var> parameters, Block body,
                    FunType funType, StorageClass storageClass,
                    boolean callsVaStart,
    boolean usesFunc) {
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.funType = funType;
        this.storageClass = storageClass;
        this.callsVaStart = callsVaStart;
        this.usesFunc = usesFunc;
    }
}
