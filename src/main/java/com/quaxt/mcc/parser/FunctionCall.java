package com.quaxt.mcc.parser;

import java.util.List;

public record FunctionCall(Identifier name, List<Exp> args) implements Exp {
}
