package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import java.util.List;

public record FunctionCall(Var name, List<Exp> args, Type type) implements Exp {
}
