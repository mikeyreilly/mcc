package com.quaxt.mcc.asm;

import com.quaxt.mcc.StaticInit;

import java.util.List;

public record StaticVariableAsm(String name, boolean global, int alignment,
                                List<StaticInit> init) implements TopLevelAsm {
    public StaticVariableAsm(String name, boolean global, int alignment,
                             List<StaticInit> init){
        this.name=name;
        this.global=global;
        this.alignment=alignment;
        this.init=init;
    }
}
