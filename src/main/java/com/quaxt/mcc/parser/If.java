package com.quaxt.mcc.parser;

import java.util.Optional;

public record If(Exp condition, Statement ifTrue, Optional<Statement> ifFalse) implements Statement {
}
