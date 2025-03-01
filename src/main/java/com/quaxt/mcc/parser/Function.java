package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.FunType;

import java.util.List;

public record Function(String name,
                       List<Var> parameters,
                       Block body,
                       FunType funType,
                       StorageClass storageClass) implements Declaration {
}
